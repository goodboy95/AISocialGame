package com.aisocialgame.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class AdminCreateRedeemCodeRequest {
    private String code;

    @NotNull(message = "tokens 不能为空")
    @Min(value = 1, message = "tokens 必须大于 0")
    private Long tokens;

    private String creditType;

    @Min(value = 1, message = "maxRedemptions 必须大于 0")
    private Integer maxRedemptions;

    private Instant validFrom;

    private Instant validUntil;

    private Boolean active;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getTokens() {
        return tokens;
    }

    public void setTokens(Long tokens) {
        this.tokens = tokens;
    }

    public String getCreditType() {
        return creditType;
    }

    public void setCreditType(String creditType) {
        this.creditType = creditType;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public void setMaxRedemptions(Integer maxRedemptions) {
        this.maxRedemptions = maxRedemptions;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
