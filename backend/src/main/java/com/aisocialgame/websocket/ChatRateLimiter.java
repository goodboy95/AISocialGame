package com.aisocialgame.websocket;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRateLimiter {
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(3);
    private final Map<String, Instant> lastSendAt = new ConcurrentHashMap<>();

    public boolean allowMessage(String playerId) {
        if (!StringUtils.hasText(playerId)) {
            return false;
        }
        Instant now = Instant.now();
        Instant previous = lastSendAt.get(playerId);
        if (previous != null && Duration.between(previous, now).compareTo(MIN_INTERVAL) < 0) {
            return false;
        }
        lastSendAt.put(playerId, now);
        return true;
    }
}
