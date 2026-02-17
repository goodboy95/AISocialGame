package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.AiChatResult;

public class AiChatResponse {
    private String content;
    private String modelKey;
    private long promptTokens;
    private long completionTokens;

    public AiChatResponse(AiChatResult result) {
        this.content = result.content();
        this.modelKey = result.modelKey();
        this.promptTokens = result.promptTokens();
        this.completionTokens = result.completionTokens();
    }

    public String getContent() {
        return content;
    }

    public String getModelKey() {
        return modelKey;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }
}
