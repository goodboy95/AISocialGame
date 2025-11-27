package com.aisocialgame.service.token;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisTokenStore implements TokenStore {
    private static final String KEY_PREFIX = "auth:token:";
    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisTokenStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
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
        return KEY_PREFIX + token;
    }
}
