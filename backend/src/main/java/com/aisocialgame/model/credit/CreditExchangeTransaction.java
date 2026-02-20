package com.aisocialgame.model.credit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_exchange_transactions")
public class CreditExchangeTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 128)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "public_tokens", nullable = false)
    private long publicTokens;

    @Column(name = "project_tokens", nullable = false)
    private long projectTokens;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(nullable = false)
    private boolean retriable;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public long getPublicTokens() {
        return publicTokens;
    }

    public void setPublicTokens(long publicTokens) {
        this.publicTokens = publicTokens;
    }

    public long getProjectTokens() {
        return projectTokens;
    }

    public void setProjectTokens(long projectTokens) {
        this.projectTokens = projectTokens;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public boolean isRetriable() {
        return retriable;
    }

    public void setRetriable(boolean retriable) {
        this.retriable = retriable;
    }
}

