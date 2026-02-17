package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;

public class AiMessageRequest {
    @NotBlank
    private String role;

    @NotBlank
    private String content;

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
