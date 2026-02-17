package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonAlias;

public class LoginRequest {
    @NotBlank
    @JsonAlias({"email", "username"})
    private String account;

    @NotBlank
    private String password;

    public String getAccount() { return account; }
    public String getPassword() { return password; }
}
