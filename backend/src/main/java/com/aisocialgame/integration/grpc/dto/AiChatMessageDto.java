package com.aisocialgame.integration.grpc.dto;

public record AiChatMessageDto(
        String role,
        String content
) {
}
