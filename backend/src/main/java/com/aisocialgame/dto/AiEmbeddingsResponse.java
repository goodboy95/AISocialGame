package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.AiEmbeddingsResult;

import java.util.List;

public class AiEmbeddingsResponse {
    private final String modelKey;
    private final int dimensions;
    private final List<List<Float>> embeddings;
    private final long promptTokens;

    public AiEmbeddingsResponse(AiEmbeddingsResult result) {
        this.modelKey = result.modelKey();
        this.dimensions = result.dimensions();
        this.embeddings = result.embeddings();
        this.promptTokens = result.promptTokens();
    }

    public String getModelKey() {
        return modelKey;
    }

    public int getDimensions() {
        return dimensions;
    }

    public List<List<Float>> getEmbeddings() {
        return embeddings;
    }

    public long getPromptTokens() {
        return promptTokens;
    }
}
