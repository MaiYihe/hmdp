package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    @Override
    public Result queryById(Long id) {

        // 布隆过滤器
        RBloomFilter<Long> bloom = redissonClient.getBloomFilter(RedisConstants.BLOOM_SHOP_ID_KEY);
        if (!bloom.contains(id)) {
            return Result.fail("商户 id 不存在");
        }

        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 从 Redis 查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断 Redis 是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 2. 返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // Redis 判空(解决缓存穿透)
        if(shopJson == ""){
            return Result.fail("店铺不存在");
        }

        // 3.1 未命中，去数据库查询
        Shop shop = this.getById(id);
        // 3.1.1 数据库也不存在，返回 404
        if (shop == null) {
            // 将空值写入 Redis(解决缓存穿透)
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return Result.fail("店铺不存在");
        }
        // 3.1.2 数据库存在，将缓存写入 Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_NULL_TTL, TimeUnit.MINUTES);

        // 返回结果
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺 id 不能为 null");
        }

        // 更新布隆过滤器
        redissonClient
                .getBloomFilter(RedisConstants.BLOOM_SHOP_ID_KEY)
                .add(shop.getId());

        // 1. 更新数据库
        this.updateById(shop);

        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
