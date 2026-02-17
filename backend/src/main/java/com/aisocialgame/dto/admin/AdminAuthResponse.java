package com.aisocialgame.dto.admin;

public class AdminAuthResponse {
    private String token;
    private String username;
    private String displayName;

    public AdminAuthResponse(String token, String username, String displayName) {
        this.token = token;
        this.username = username;
        this.displayName = displayName;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }
}
