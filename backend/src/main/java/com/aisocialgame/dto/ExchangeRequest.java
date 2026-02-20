package com.aisocialgame.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ExchangeRequest {
    @NotNull(message = "兑换数量不能为空")
    @Min(value = 1, message = "兑换数量必须大于 0")
    private Long amount;

    private String requestId;

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}

