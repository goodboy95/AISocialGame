package com.aisocialgame.dto.admin;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class AdminBanRequest {
    private boolean permanent = true;

    private Instant expiresAt;

    @NotBlank
    private String reason;

    public boolean isPermanent() {
        return permanent;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getReason() {
        return reason;
    }
}
