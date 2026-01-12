package com.hmdp.utils;

import lombok.Data;

@Data
public class RedisData<T> {
    private Long expireTime;
    private T data;
}
