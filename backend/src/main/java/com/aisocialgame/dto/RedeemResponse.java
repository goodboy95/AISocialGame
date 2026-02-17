package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.RedeemResult;

public class RedeemResponse {
    private final boolean success;
    private final long tokensGranted;
    private final String creditType;
    private final String errorMessage;
    private final BalanceView balance;

    public RedeemResponse(RedeemResult result) {
        this.success = result.success();
        this.tokensGranted = result.tokensGranted();
        this.creditType = result.creditType();
        this.errorMessage = result.errorMessage();
        this.balance = new BalanceView(result.balance());
    }

    public boolean isSuccess() {
        return success;
    }

    public long getTokensGranted() {
        return tokensGranted;
    }

    public String getCreditType() {
        return creditType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public BalanceView getBalance() {
        return balance;
    }
}
