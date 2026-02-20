package com.aisocialgame.model.credit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_checkin_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_credit_checkin_user_project_date", columnNames = {"user_id", "project_key", "checkin_date"}),
        @UniqueConstraint(name = "uk_credit_checkin_request_id", columnNames = {"request_id"})
})
public class CreditCheckinRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "tokens_granted", nullable = false)
    private long tokensGranted;

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

    public LocalDate getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate(LocalDate checkinDate) {
        this.checkinDate = checkinDate;
    }

    public long getTokensGranted() {
        return tokensGranted;
    }

    public void setTokensGranted(long tokensGranted) {
        this.tokensGranted = tokensGranted;
    }
}

