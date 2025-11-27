package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;

public class JoinRoomRequest {
    @NotBlank
    private String displayName;

    public String getDisplayName() { return displayName; }
}
