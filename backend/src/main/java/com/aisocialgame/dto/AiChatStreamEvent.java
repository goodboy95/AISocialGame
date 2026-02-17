package com.aisocialgame.dto;

public class AiChatStreamEvent {
    private String content;
    private boolean done;
    private String modelKey;
    private Long promptTokens;
    private Long completionTokens;

    public AiChatStreamEvent(String content, boolean done) {
        this.content = content;
        this.done = done;
    }

    public static AiChatStreamEvent done(AiChatResponse response) {
        AiChatStreamEvent event = new AiChatStreamEvent("", true);
        event.modelKey = response.getModelKey();
        event.promptTokens = response.getPromptTokens();
        event.completionTokens = response.getCompletionTokens();
        return event;
    }

    public String getContent() {
        return content;
    }

    public boolean isDone() {
        return done;
    }

    public String getModelKey() {
        return modelKey;
    }

    public Long getPromptTokens() {
        return promptTokens;
    }

    public Long getCompletionTokens() {
        return completionTokens;
    }
}
