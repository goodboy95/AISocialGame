package com.aisocialgame.integration.grpc.dto;

import java.time.Instant;

public record ExchangeHistorySnapshot(
        long id,
        String requestId,
        long exchangedTokens,
        long publicBefore,
        long publicAfter,
        long projectPermanentBefore,
        long projectPermanentAfter,
        Instant createdAt
) {
}
