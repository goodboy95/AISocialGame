package com.aisocialgame.integration.grpc.dto;

public record AiOcrResult(
        String requestId,
        String modelKey,
        String outputType,
        String content,
        String rawJson
) {
}
