package com.aisocialgame.integration.grpc.dto;

public record CheckinResult(
        boolean success,
        long tokensGranted,
        boolean alreadyCheckedIn,
        String errorMessage,
        BalanceSnapshot balance
) {
}
