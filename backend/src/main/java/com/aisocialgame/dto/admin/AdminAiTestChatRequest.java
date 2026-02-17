package com.aisocialgame.dto.admin;

import com.aisocialgame.dto.AiMessageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public class AdminAiTestChatRequest {
    @PositiveOrZero
    private long userId;

    private String sessionId;

    private String model;

    @Valid
    @NotEmpty
    private List<AiMessageRequest> messages;

    public long getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getModel() {
        return model;
    }

    public List<AiMessageRequest> getMessages() {
        return messages;
    }
}
