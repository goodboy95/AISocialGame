package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class CreateRoomRequest {
    @NotBlank
    private String roomName;
    @NotNull
    private Boolean isPrivate;
    private String password;
    private String commMode;
    private Map<String, Object> config;

    public String getRoomName() { return roomName; }
    public Boolean getIsPrivate() { return isPrivate; }
    public String getPassword() { return password; }
    public String getCommMode() { return commMode; }
    public Map<String, Object> getConfig() { return config; }
}
