package com.hmdp.infrastructure.cache;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;

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

    // 解决缓存雪崩 -> 真实 TTL + 随机抖动
    public static long randomTTL(long baseSeconds) {
        return baseSeconds + ThreadLocalRandom.current().nextLong(0, baseSeconds / 10);
    }

    public <T> T queryWithBloom(
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
        long realTTL = randomTTL(cacheRule.ttlSeconds());
        stringRedisTemplate.opsForValue()
                .set(key, JSONUtil.toJsonStr(obj), realTTL, TimeUnit.SECONDS);
        localCache.put(key, obj);

        return obj;
    }
}
