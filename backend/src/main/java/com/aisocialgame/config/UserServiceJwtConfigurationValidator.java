package com.aisocialgame.config;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Fail-closed validation for AISocialGame's least-privilege UserService caller identity. */
public final class UserServiceJwtConfigurationValidator {
    public static final String REQUIRED_CALLER_ID = "aisocialgame";
    public static final String REQUIRED_ISSUER = "aisocialgame";
    public static final String REQUIRED_AUDIENCE = "aienie-userservice-grpc";
    public static final long MIN_TTL_SECONDS = 30L;
    public static final long MAX_TTL_SECONDS = 900L;
    public static final List<String> REQUIRED_SCOPES = List.of(
            "user.auth.session.read",
            "user.directory.read",
            "user.ban.read",
            "user.ban.write"
    );

    private UserServiceJwtConfigurationValidator() {
    }

    public static void validate(AppProperties.UserServiceJwt configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("UserService caller JWT configuration is required");
        }
        validate(
                configuration.getCallerId(),
                configuration.getIssuer(),
                configuration.getSecret(),
                configuration.getAudience(),
                configuration.getTtlSeconds(),
                configuration.getScopes()
        );
    }

    public static void validate(String callerId,
                                String issuer,
                                String secret,
                                String audience,
                                long ttlSeconds,
                                String scopes) {
        if (!REQUIRED_CALLER_ID.equals(callerId) || !REQUIRED_ISSUER.equals(issuer)) {
            throw new IllegalArgumentException("UserService caller JWT identity is not canonical");
        }
        validateSecret(secret);
        if (!REQUIRED_AUDIENCE.equals(audience)) {
            throw new IllegalArgumentException("UserService caller JWT audience is not canonical");
        }
        if (ttlSeconds < MIN_TTL_SECONDS || ttlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("UserService caller JWT TTL is outside the safe bound");
        }
        if (!Set.copyOf(REQUIRED_SCOPES).equals(normalizedScopes(scopes))) {
            throw new IllegalArgumentException("UserService caller JWT scopes violate least privilege");
        }
    }

    public static Set<String> normalizedScopes(String raw) {
        if (raw == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw.split(",", -1)) {
            if (value.isEmpty() || !value.equals(value.trim()) || !result.add(value)) {
                return Set.of();
            }
        }
        return Set.copyOf(result);
    }

    private static void validateSecret(String secret) {
        int length = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (isPlaceholder(secret)
                || secret == null
                || !secret.equals(secret.trim())
                || secret.chars().anyMatch(Character::isISOControl)
                || length < 32
                || length > 4_096) {
            throw new IllegalArgumentException("UserService caller JWT secret must contain 32..4096 UTF-8 bytes");
        }
    }

    private static boolean isPlaceholder(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.isEmpty()
                || normalized.contains("REPLACE")
                || normalized.contains("CHANGE_ME")
                || normalized.contains("CHANGE-ME")
                || normalized.contains("CHANGEME")
                || normalized.contains("PLACEHOLDER")
                || normalized.startsWith("<")
                || normalized.endsWith(">");
    }
}
