package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.ExchangeHistorySnapshot;

import java.time.format.DateTimeFormatter;

public class ExchangeHistoryView {
    private final String requestId;
    private final long exchangedTokens;
    private final long publicBefore;
    private final long publicAfter;
    private final long projectPermanentBefore;
    private final long projectPermanentAfter;
    private final String createdAt;

    public ExchangeHistoryView(ExchangeHistorySnapshot snapshot) {
        this.requestId = snapshot.requestId();
        this.exchangedTokens = snapshot.exchangedTokens();
        this.publicBefore = snapshot.publicBefore();
        this.publicAfter = snapshot.publicAfter();
        this.projectPermanentBefore = snapshot.projectPermanentBefore();
        this.projectPermanentAfter = snapshot.projectPermanentAfter();
        this.createdAt = snapshot.createdAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(snapshot.createdAt());
    }

    public String getRequestId() {
        return requestId;
    }

    public long getExchangedTokens() {
        return exchangedTokens;
    }

    public long getPublicBefore() {
        return publicBefore;
    }

    public long getPublicAfter() {
        return publicAfter;
    }

    public long getProjectPermanentBefore() {
        return projectPermanentBefore;
    }

    public long getProjectPermanentAfter() {
        return projectPermanentAfter;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
