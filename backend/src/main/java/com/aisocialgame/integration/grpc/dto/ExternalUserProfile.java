package com.aisocialgame.integration.grpc.dto;

import java.time.Instant;

public record ExternalUserProfile(
        long userId,
        String username,
        String email,
        String avatarUrl,
        boolean active,
        Instant bannedUntil,
        Instant createdAt
) {
}
