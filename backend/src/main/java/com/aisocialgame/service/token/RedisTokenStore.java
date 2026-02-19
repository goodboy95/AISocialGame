package com.aisocialgame.service.token;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisTokenStore implements TokenStore {
    private static final String DEFAULT_KEY_PREFIX = "auth:token:";
    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final String keyPrefix;

    public RedisTokenStore(StringRedisTemplate redisTemplate, Duration ttl, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? DEFAULT_KEY_PREFIX : keyPrefix;
    }

    @Override
    public void store(String token, String userId) {
        redisTemplate.opsForValue().set(buildKey(token), userId, ttl);
    }

    @Override
    public String getUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return redisTemplate.opsForValue().get(buildKey(token));
    }

    @Override
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        redisTemplate.delete(buildKey(token));
    }

    private String buildKey(String token) {
        return keyPrefix + token;
    }
}
