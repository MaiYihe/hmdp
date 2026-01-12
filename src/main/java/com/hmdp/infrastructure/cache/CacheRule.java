package com.hmdp.infrastructure.cache;

import java.util.concurrent.TimeUnit;

public final class CacheRule {

    private CacheMode mode = CacheMode.NORMAL;

    private String bloomKey;

    private Long nullTtlSeconds; // 默认以秒作为单位
    private Long redisTtlSeconds;

    private Long logicalTtlSeconds; // 逻辑过期时的真实 TTL

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
        this.redisTtlSeconds = unit.toSeconds(ttl);
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

    public long redisTtlSeconds() {
        check();
        return redisTtlSeconds;
    }

    public long nullTtlSeconds() {
        check();
        return nullTtlSeconds;
    }

    private void check() {
        if (mode == CacheMode.NORMAL) {
            if (redisTtlSeconds == null || redisTtlSeconds <= 0) {
                throw new IllegalStateException("ttl must be set in NORMAL mode");
            }
            if (nullTtlSeconds == null || nullTtlSeconds <= 0) {
                throw new IllegalStateException("nullTTL must be set");
            }
        }

        if (mode == CacheMode.LOGICAL_EXPIRE) {
            if (logicalTtlSeconds == null || logicalTtlSeconds <= 0) {
                throw new IllegalStateException("logicalTtl must be set");
            }
            if (redisTtlSeconds == null || redisTtlSeconds <= 0) {
                throw new IllegalStateException("redisTtl must be set");
            }
            if (redisTtlSeconds <= logicalTtlSeconds) {
                throw new IllegalStateException("redisTtl must be > logicalTtl");
            }
        }
    }
}
