package com.aisocialgame.integration.grpc.dto;

public record AiChatResult(
        String content,
        String modelKey,
        long promptTokens,
        long completionTokens
) {
}
