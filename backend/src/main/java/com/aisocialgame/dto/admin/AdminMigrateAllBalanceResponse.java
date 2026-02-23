package com.aisocialgame.dto.admin;

import java.util.List;

public class AdminMigrateAllBalanceResponse {
    private final long scanned;
    private final long success;
    private final long failed;
    private final int batchSize;
    private final List<FailureItem> failures;

    public AdminMigrateAllBalanceResponse(long scanned, long success, long failed, int batchSize, List<FailureItem> failures) {
        this.scanned = scanned;
        this.success = success;
        this.failed = failed;
        this.batchSize = batchSize;
        this.failures = failures;
    }

    public long getScanned() {
        return scanned;
    }

    public long getSuccess() {
        return success;
    }

    public long getFailed() {
        return failed;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public List<FailureItem> getFailures() {
        return failures;
    }

    public record FailureItem(long userId, String message) {}
}
