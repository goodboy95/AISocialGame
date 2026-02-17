package com.aisocialgame.dto;

public class SsoUrlResponse {
    private final String loginUrl;
    private final String registerUrl;

    public SsoUrlResponse(String loginUrl, String registerUrl) {
        this.loginUrl = loginUrl;
        this.registerUrl = registerUrl;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public String getRegisterUrl() {
        return registerUrl;
    }
}
