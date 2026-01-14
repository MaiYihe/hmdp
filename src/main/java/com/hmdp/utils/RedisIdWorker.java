package com.hmdp.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisIdWorker {

    // 自定义纪元时间（比如 2024-01-01）
    private static final long BEGIN_TIMESTAMP = 1704067200L; // 秒

    // 序列号占用位数
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        // 1. 当前时间戳（秒）
        long nowSecond = System.currentTimeMillis() / 1000;
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 当天的序列号 key
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String redisKey = "icr:" + keyPrefix + ":" + date;

        // 3. Redis 自增
        Long count = stringRedisTemplate.opsForValue().increment(redisKey);

        // 4. 组合 ID（左移运算 + 或运算）
        return (timestamp << COUNT_BITS) | count;
    }
}
