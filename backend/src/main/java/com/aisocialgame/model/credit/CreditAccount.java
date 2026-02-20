package com.aisocialgame.model.credit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_credit_account_user_project", columnNames = {"user_id", "project_key"})
})
public class CreditAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "temp_balance", nullable = false)
    private long tempBalance;

    @Column(name = "temp_expires_at")
    private Instant tempExpiresAt;

    @Column(name = "permanent_balance", nullable = false)
    private long permanentBalance;

    @Version
    private Long version;

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

    public long getTempBalance() {
        return tempBalance;
    }

    public void setTempBalance(long tempBalance) {
        this.tempBalance = tempBalance;
    }

    public Instant getTempExpiresAt() {
        return tempExpiresAt;
    }

    public void setTempExpiresAt(Instant tempExpiresAt) {
        this.tempExpiresAt = tempExpiresAt;
    }

    public long getPermanentBalance() {
        return permanentBalance;
    }

    public void setPermanentBalance(long permanentBalance) {
        this.permanentBalance = permanentBalance;
    }
}

