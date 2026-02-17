package com.aisocialgame.integration.grpc.dto;

public record AiOcrParams(
        String imageUrl,
        String imageBase64,
        String documentUrl,
        String pages,
        String outputType
) {
}
