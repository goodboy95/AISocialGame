package com.aisocialgame.integration.grpc.dto;

import java.time.Instant;

public record RedemptionRecordSnapshot(
        long id,
        String code,
        long tokensGranted,
        String creditType,
        String projectKey,
        Instant redeemedAt
) {
}
