package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.LedgerEntrySnapshot;

import java.time.format.DateTimeFormatter;

public class LedgerEntryView {
    private final String id;
    private final String type;
    private final long tokens;
    private final String reason;
    private final String createdAt;

    public LedgerEntryView(LedgerEntrySnapshot snapshot) {
        this.id = String.valueOf(snapshot.id());
        this.type = snapshot.type();
        this.tokens = snapshot.tokenDeltaTemp() + snapshot.tokenDeltaPermanent() + snapshot.tokenDeltaPublic();
        this.reason = snapshot.source();
        this.createdAt = snapshot.createdAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(snapshot.createdAt());
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public long getTokens() {
        return tokens;
    }

    public String getReason() {
        return reason;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
