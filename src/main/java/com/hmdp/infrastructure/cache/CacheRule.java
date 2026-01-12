package com.hmdp.infrastructure.cache;

import java.util.concurrent.TimeUnit;

public final class CacheRule {
    private String bloomKey;
    private Long nullTtlSeconds; // 默认以秒作为单位
    private Long ttlSeconds;

    private CacheRule() {
    }

    public static CacheRule create() {
        return new CacheRule();
    }

    // ===== fluent setters =====

    public CacheRule bloom(String bloomKey) {
        this.bloomKey = bloomKey;
        return this;
    }

    public CacheRule ttl(long ttl, TimeUnit unit) {
        this.ttlSeconds = unit.toSeconds(ttl);
        return this;
    }

    public CacheRule nullTTL(long ttl, TimeUnit unit) {
        this.nullTtlSeconds = unit.toSeconds(ttl);
        return this;
    }

    // ===== getters with validation =====

    public String bloomKey() {
        return bloomKey;
    }

    public long ttlSeconds() {
        check();
        return ttlSeconds;
    }

    public long nullTtlSeconds() {
        check();
        return nullTtlSeconds;
    }

    private void check() {
        if (ttlSeconds == null || ttlSeconds <= 0) {
            throw new IllegalStateException("ttl must be set and > 0");
        }
        if (nullTtlSeconds == null || nullTtlSeconds <= 0) {
            throw new IllegalStateException("nullTTL must be set and > 0");
        }
        if (nullTtlSeconds >= ttlSeconds) {
            throw new IllegalStateException("nullTTL must be < ttl");
        }
    }
}
