package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;

public class NightActionRequest {
    @NotBlank
    private String action;

    private String targetPlayerId;
    private boolean useHeal;

    public String getAction() {
        return action;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    public boolean isUseHeal() {
        return useHeal;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setTargetPlayerId(String targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public void setUseHeal(boolean useHeal) {
        this.useHeal = useHeal;
    }
}
