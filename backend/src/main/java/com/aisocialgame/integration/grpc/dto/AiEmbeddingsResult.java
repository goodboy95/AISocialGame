package com.aisocialgame.integration.grpc.dto;

import java.util.List;

public record AiEmbeddingsResult(
        String modelKey,
        int dimensions,
        List<List<Float>> embeddings,
        long promptTokens
) {
}
