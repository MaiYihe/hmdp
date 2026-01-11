package com.hmdp.config;

import java.util.List;

import javax.annotation.PostConstruct;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.RedisConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 布隆过滤器
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomConfig {

    private final RedissonClient redissonClient;
    private final ShopMapper shopMapper;

    @PostConstruct
    public void initBloom() {
        RBloomFilter<Long> bloom = redissonClient.getBloomFilter(RedisConstants.BLOOM_SHOP_ID_KEY);

        if (!bloom.isExists() || bloom.count() == 0) {
            bloom.tryInit(100_000_000L, 0.001);

            // 从 DB 灌数据
            List<Long> ids = shopMapper.selectAllIds();

            for (Long id : ids) {
                bloom.add(id);
            }

            log.info("Bloom loaded {} shop ids",ids.size());
        }
    }
}
