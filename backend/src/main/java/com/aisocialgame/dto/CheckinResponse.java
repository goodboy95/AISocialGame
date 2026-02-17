package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.CheckinResult;

public class CheckinResponse {
    private final boolean success;
    private final long tokensGranted;
    private final boolean alreadyCheckedIn;
    private final String errorMessage;
    private final BalanceView balance;

    public CheckinResponse(CheckinResult result) {
        this.success = result.success();
        this.tokensGranted = result.tokensGranted();
        this.alreadyCheckedIn = result.alreadyCheckedIn();
        this.errorMessage = result.errorMessage();
        this.balance = new BalanceView(result.balance());
    }

    public boolean isSuccess() {
        return success;
    }

    public long getTokensGranted() {
        return tokensGranted;
    }

    public boolean isAlreadyCheckedIn() {
        return alreadyCheckedIn;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public BalanceView getBalance() {
        return balance;
    }
}
