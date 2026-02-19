package com.aisocialgame.config;

import com.aisocialgame.service.token.InMemoryTokenStore;
import com.aisocialgame.service.token.RedisTokenStore;
import com.aisocialgame.service.token.TokenStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class TokenStoreConfig {

    @Bean
    @Profile("test")
    public TokenStore inMemoryTokenStore(@Value("${app.auth.token-ttl-hours:24}") long hours) {
        return new InMemoryTokenStore(Duration.ofHours(hours));
    }

    @Bean
    @Profile("!test")
    public TokenStore redisTokenStore(StringRedisTemplate redisTemplate,
                                      @Value("${app.auth.token-ttl-hours:168}") long hours,
                                      @Value("${app.redis.token-key-prefix:}") String keyPrefix,
                                      @Value("${app.project-key:aisocialgame}") String projectKey) {
        String resolvedKeyPrefix = resolveKeyPrefix(keyPrefix, projectKey);
        return new RedisTokenStore(redisTemplate, Duration.ofHours(hours), resolvedKeyPrefix);
    }

    private String resolveKeyPrefix(String configuredPrefix, String projectKey) {
        String value = configuredPrefix;
        if (value == null || value.isBlank()) {
            value = projectKey + ":auth:token:";
        }
        return value.endsWith(":") ? value : value + ":";
    }
}
