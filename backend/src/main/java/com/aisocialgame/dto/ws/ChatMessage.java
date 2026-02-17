package com.aisocialgame.dto.ws;

public record ChatMessage(
        String id,
        String roomId,
        String senderId,
        String senderName,
        String senderAvatar,
        String type,
        String content,
        long timestamp
) {
}
