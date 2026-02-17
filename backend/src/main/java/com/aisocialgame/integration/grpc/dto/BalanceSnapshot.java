package com.aisocialgame.integration.grpc.dto;

import java.time.Instant;

public record BalanceSnapshot(
        long publicPermanentTokens,
        long projectTempTokens,
        long projectPermanentTokens,
        Instant projectTempExpiresAt
) {
    public long totalTokens() {
        return publicPermanentTokens + projectTempTokens + projectPermanentTokens;
    }

    public static BalanceSnapshot empty() {
        return new BalanceSnapshot(0, 0, 0, null);
    }
}
