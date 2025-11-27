package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;

public class VoteRequest {
    private boolean abstain;

    @NotBlank(message = "请提供投票目标")
    private String targetPlayerId;

    public boolean isAbstain() {
        return abstain;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setAbstain(boolean abstain) {
        this.abstain = abstain;
    }

    public void setTargetPlayerId(String targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }
}
