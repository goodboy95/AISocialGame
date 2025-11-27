package com.aisocialgame.service.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTokenStore implements TokenStore {
    private final Map<String, TokenRecord> tokens = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryTokenStore(Duration ttl) {
        this.ttl = ttl;
    }

    @Override
    public void store(String token, String userId) {
        tokens.put(token, new TokenRecord(userId, Instant.now().plus(ttl)));
    }

    @Override
    public String getUserId(String token) {
        TokenRecord record = tokens.get(token);
        if (record == null) {
            return null;
        }
        if (record.expiresAt().isBefore(Instant.now())) {
            tokens.remove(token);
            return null;
        }
        return record.userId();
    }

    @Override
    public void revoke(String token) {
        tokens.remove(token);
    }

    private record TokenRecord(String userId, Instant expiresAt) {}
}
