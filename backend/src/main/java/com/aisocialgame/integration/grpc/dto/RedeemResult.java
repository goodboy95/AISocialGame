package com.aisocialgame.integration.grpc.dto;

public record RedeemResult(
        boolean success,
        long tokensGranted,
        String creditType,
        String errorMessage,
        BalanceSnapshot balance
) {
}
