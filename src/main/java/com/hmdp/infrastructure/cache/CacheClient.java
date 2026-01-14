package com.hmdp.infrastructure.cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.utils.RedisData;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<String, Object> localCache;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    // 解决缓存雪崩 -> 真实 TTL + 随机抖动
    public static long randomTTL(long baseSeconds) {
        return baseSeconds + ThreadLocalRandom.current().nextLong(0, baseSeconds / 10);
    }

    // 普通_低价值 key 带布隆查询
    public <T> T queryCodeSoftWithBloom(
            String key,
            Long id,
            CacheRule cacheRule,
            Class<T> type,
            Supplier<T> dbFallback) {

        // === 查 Caffeine（L1）
        @SuppressWarnings("unchecked")
        T obj = (T) localCache.getIfPresent(key);
        if (obj != null) {
            return obj;
        }

        // === Bloom 防缓存穿透
        RBloomFilter<Long> bloom = redissonClient.getBloomFilter(cacheRule.bloomKey());
        if (!bloom.contains(id)) {
            return null;
        }

        // === 查 Redis（L2）
        String json = stringRedisTemplate.opsForValue().get(key);
        // 3.1 命中缓存
        if (StrUtil.isNotBlank(json)) {
            obj = JSONUtil.toBean(json, type);
            // 回填 Caffeine
            localCache.put(key, obj);
            return obj;
        }

        // 3.2 命中空值
        if ("".equals(json)) {
            return null;
        }

        // === 查 DB
        obj = dbFallback.get();
        if (obj == null) {
            // Redis 写空值，防止缓存穿透
            stringRedisTemplate.opsForValue()
                    .set(key, "", cacheRule.nullTtlSeconds(), TimeUnit.MINUTES);
            return null;
        }

        // === 回填 Redis + Caffeine
        long realTTL = randomTTL(cacheRule.redisTtlSeconds());
        stringRedisTemplate.opsForValue()
                .set(key, JSONUtil.toJsonStr(obj), realTTL, TimeUnit.SECONDS);
        localCache.put(key, obj);

        return obj;
    }

    // 普通_高价值 key 带布隆查询
    public <T> T queryCodeHardWithBloom(
            String key,
            Long id,
            CacheRule cacheRule,
            Class<T> type,
            Supplier<T> dbFallback) {
        if (cacheRule.redisTtlSeconds() >= 30) {
            throw new IllegalArgumentException("TTL must be less than 30 seconds");
        }

        return queryCodeSoftWithBloom(key, id, cacheRule, type, dbFallback);
    }

    // 热点_低价值 key 查询
    public <T> T queryHotHard(
            String key,
            Long id,
            CacheRule cacheRule,
            Class<T> type,
            Supplier<T> dbFallback) {
        // 1. 查 Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 获取到逻辑过期的 Redis 类
        RedisData<T> redisData;
        try {
            JavaType redisDataType = objectMapper.getTypeFactory()
                    .constructParametricType(RedisData.class, type);

            redisData = objectMapper.readValue(json, redisDataType);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize redis data, key={}", key, e);
            // 当缓存损坏，直接当成 miss
            stringRedisTemplate.delete(key);
            return null;
        }
        // 将逻辑过期类当中的数据读取出来
        T data = JSONUtil.toBean(
                JSONUtil.toJsonStr(redisData.getData()), type);

        long expireTime = redisData.getExpireTime();
        if (expireTime > System.currentTimeMillis()) {
            return data; // 没过期，直接返回
        }

        // 2. 逻辑过期了 → 尝试抢锁
        RLock lock = redissonClient.getLock("lock:rebuild:" + key);
        boolean locked = false;
        try {
            // 现在能不能马上拿到锁？拿到就给 10 秒租期，拿不到就算了
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态（非常重要）
            log.warn("Cache rebuild lock interrupted, key={}", key, e);
        }

        if (locked) {
            // 开新线程异步重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    T newData = dbFallback.get();

                    RedisData<T> newRedisData = new RedisData<>();
                    newRedisData.setData(newData);
                    newRedisData.setExpireTime(
                            System.currentTimeMillis() + cacheRule.redisTtlSeconds() * 1000);

                    // Redis 物理 TTL ，要比逻辑过期长
                    stringRedisTemplate.opsForValue().set(
                            key,
                            JSONUtil.toJsonStr(newRedisData),
                            cacheRule.redisTtlSeconds(), // 比如 1 天
                            TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("Cache rebuild failed, key={}", key, e);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            });
        }

        // 3. 无论是否拿到锁，都返回旧数据
        return data;
    }

    // 一个专门为 Redis 逻辑过期重建而存在的线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            4, // 核心线程
            8, // 最大线程
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "cache-rebuild-thread");
                }
            },
            new ThreadPoolExecutor.DiscardPolicy());
}
