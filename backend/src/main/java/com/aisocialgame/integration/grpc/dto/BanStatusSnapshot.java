package com.aisocialgame.integration.grpc.dto;

import java.time.Instant;

public record BanStatusSnapshot(
        boolean banned,
        String banType,
        String reason,
        Instant expiresAt,
        Instant bannedAt
) {
}
