package com.aisocialgame.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AdminReverseBalanceRequest {
    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "originalRequestId 不能为空")
    private String originalRequestId;

    @NotBlank(message = "冲正原因不能为空")
    private String reason;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOriginalRequestId() {
        return originalRequestId;
    }

    public void setOriginalRequestId(String originalRequestId) {
        this.originalRequestId = originalRequestId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

