package com.hmdp.utils;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CacheClient {

    // 解决缓存雪崩 -> 真实 TTL + 随机抖动
    long random = ThreadLocalRandom.current().nextLong(0, 300); // 0~5分钟

    public static long randomTTL(long baseSeconds) {
        return baseSeconds + ThreadLocalRandom.current().nextLong(0, baseSeconds / 10);
    }
}
