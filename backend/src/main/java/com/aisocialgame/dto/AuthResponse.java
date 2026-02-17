package com.aisocialgame.dto;

public class AuthResponse {
    private String token;
    private AuthUserView user;

    public AuthResponse(String token, AuthUserView user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public AuthUserView getUser() { return user; }
}
