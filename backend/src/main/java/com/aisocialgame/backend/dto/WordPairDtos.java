package com.aisocialgame.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class WordPairDtos {

    private WordPairDtos() {}

    public record WordPairView(
            long id,
            String topic,
            @JsonProperty("civilian_word") String civilianWord,
            @JsonProperty("undercover_word") String undercoverWord,
            String difficulty,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("updated_at") String updatedAt) {}

    public record WordPairPayload(
            String topic,
            @JsonProperty("civilian_word") String civilianWord,
            @JsonProperty("undercover_word") String undercoverWord,
            String difficulty) {}

    public record BulkImportPayload(List<WordPairPayload> items) {}

    public record BulkImportResponse(List<WordPairView> items, int created) {}

    public record ExportResponse(List<WordPairView> items) {}
}
