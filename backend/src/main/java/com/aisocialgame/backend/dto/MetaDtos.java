package com.aisocialgame.backend.dto;

import java.util.List;

public final class MetaDtos {

    private MetaDtos() {}

    public record AiStyle(String key, String label, String description) {}

    public record AiStyleResponse(List<AiStyle> styles) {}
}
