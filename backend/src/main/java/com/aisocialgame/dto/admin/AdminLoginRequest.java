package com.aisocialgame.dto.admin;

import jakarta.validation.constraints.NotBlank;

public class AdminLoginRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
