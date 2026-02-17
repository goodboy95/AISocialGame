package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SsoCallbackRequest {
    @NotBlank(message = "accessToken 不能为空")
    private String accessToken;
    @NotNull(message = "userId 不能为空")
    private Long userId;
    @NotBlank(message = "username 不能为空")
    private String username;
    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
