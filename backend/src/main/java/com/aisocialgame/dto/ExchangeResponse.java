package com.aisocialgame.dto;

import com.aisocialgame.service.CreditExchangeResult;

public class ExchangeResponse {
    private final boolean success;
    private final String requestId;
    private final long exchangedTokens;
    private final BalanceView balance;

    public ExchangeResponse(CreditExchangeResult result) {
        this.success = true;
        this.requestId = result.requestId();
        this.exchangedTokens = result.exchangedTokens();
        this.balance = new BalanceView(result.balance());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getRequestId() {
        return requestId;
    }

    public long getExchangedTokens() {
        return exchangedTokens;
    }

    public BalanceView getBalance() {
        return balance;
    }
}

