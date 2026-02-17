package com.aisocialgame.dto.admin;

import jakarta.validation.constraints.NotBlank;

public class AdminUnbanRequest {
    @NotBlank
    private String reason;

    public String getReason() {
        return reason;
    }
}
