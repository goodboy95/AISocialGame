package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;

public class AddAiRequest {
    @NotBlank
    private String personaId;

    public String getPersonaId() { return personaId; }
}
