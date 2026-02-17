package com.aisocialgame.integration.grpc.dto;

import java.time.Instant;
import java.util.Map;

public record LedgerEntrySnapshot(
        long id,
        String requestId,
        String projectKey,
        String type,
        long tokenDeltaTemp,
        long tokenDeltaPermanent,
        long tokenDeltaPublic,
        long balanceTemp,
        long balancePermanent,
        long balancePublic,
        String source,
        Instant createdAt,
        Map<String, String> metadata
) {
}
