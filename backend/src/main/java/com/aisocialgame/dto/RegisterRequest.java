package com.aisocialgame.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @JsonAlias({"account"})
    private String username;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 6, message = "密码至少6位")
    private String password;

    @NotBlank
    private String nickname;

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
}
