package com.aisocialgame.dto.admin;

import com.aisocialgame.model.credit.CreditRedeemCode;

import java.time.Instant;

public class AdminRedeemCodeResponse {
    private final Long id;
    private final String code;
    private final String creditType;
    private final long tokens;
    private final boolean active;
    private final Instant validFrom;
    private final Instant validUntil;
    private final Integer maxRedemptions;
    private final int redeemedCount;

    public AdminRedeemCodeResponse(CreditRedeemCode redeemCode) {
        this.id = redeemCode.getId();
        this.code = redeemCode.getCode();
        this.creditType = redeemCode.getCreditType();
        this.tokens = redeemCode.getTokens();
        this.active = redeemCode.isActive();
        this.validFrom = redeemCode.getValidFrom();
        this.validUntil = redeemCode.getValidUntil();
        this.maxRedemptions = redeemCode.getMaxRedemptions();
        this.redeemedCount = redeemCode.getRedeemedCount();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCreditType() {
        return creditType;
    }

    public long getTokens() {
        return tokens;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public int getRedeemedCount() {
        return redeemedCount;
    }
}
