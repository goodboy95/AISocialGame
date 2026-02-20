package com.aisocialgame.service;

import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;

public record CreditExchangeResult(
        String requestId,
        long exchangedTokens,
        BalanceSnapshot balance
) {
}

