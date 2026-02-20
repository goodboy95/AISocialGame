package com.aisocialgame.dto.admin;

import jakarta.validation.constraints.NotNull;

public class AdminMigrateBalanceRequest {
    @NotNull(message = "userId 不能为空")
    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

