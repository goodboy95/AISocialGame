package com.aisocialgame.adminauth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AdminRateLimiter {
    private final StringRedisTemplate redis;

    @Autowired
    public AdminRateLimiter(ObjectProvider<StringRedisTemplate> provider) {
        this.redis = provider.getIfAvailable();
    }

    AdminRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean allow(String dimension, int limit, Duration window) {
        if (redis == null) {
            return false;
        }
        try {
            String key = "aisocialgame:admin-auth:rate:" + dimension;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }
            return count != null && count <= limit;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
