package com.aisocialgame.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AdminAdjustBalanceRequest {
    @NotNull(message = "userId 不能为空")
    private Long userId;

    private long deltaTemp;

    private long deltaPermanent;

    @NotBlank(message = "调整原因不能为空")
    private String reason;

    private String requestId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public long getDeltaTemp() {
        return deltaTemp;
    }

    public void setDeltaTemp(long deltaTemp) {
        this.deltaTemp = deltaTemp;
    }

    public long getDeltaPermanent() {
        return deltaPermanent;
    }

    public void setDeltaPermanent(long deltaPermanent) {
        this.deltaPermanent = deltaPermanent;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}

