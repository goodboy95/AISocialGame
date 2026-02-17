package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;

import java.time.Instant;

public class BalanceView {
    private long publicPermanentTokens;
    private long projectTempTokens;
    private long projectPermanentTokens;
    private long totalTokens;
    private Instant projectTempExpiresAt;

    public BalanceView(BalanceSnapshot snapshot) {
        this.publicPermanentTokens = snapshot.publicPermanentTokens();
        this.projectTempTokens = snapshot.projectTempTokens();
        this.projectPermanentTokens = snapshot.projectPermanentTokens();
        this.totalTokens = snapshot.totalTokens();
        this.projectTempExpiresAt = snapshot.projectTempExpiresAt();
    }

    public long getPublicPermanentTokens() {
        return publicPermanentTokens;
    }

    public long getProjectTempTokens() {
        return projectTempTokens;
    }

    public long getProjectPermanentTokens() {
        return projectPermanentTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public Instant getProjectTempExpiresAt() {
        return projectTempExpiresAt;
    }
}
