package com.aisocialgame.model.credit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_redemption_records")
public class CreditRedemptionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "tokens_granted", nullable = false)
    private long tokensGranted;

    @Column(name = "credit_type", nullable = false, length = 32)
    private String creditType;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public long getTokensGranted() {
        return tokensGranted;
    }

    public void setTokensGranted(long tokensGranted) {
        this.tokensGranted = tokensGranted;
    }

    public String getCreditType() {
        return creditType;
    }

    public void setCreditType(String creditType) {
        this.creditType = creditType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }

    public void setRedeemedAt(Instant redeemedAt) {
        this.redeemedAt = redeemedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

