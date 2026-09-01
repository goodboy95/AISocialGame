package com.aisocialgame.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StagingTrustPolicyTest {
    @Test
    void stagingRequiresAllFixedRootsAndProductionRejectsThem() {
        assertDoesNotThrow(() -> new StagingTrustPolicy(
                "test", StagingTrustPolicy.STAGING_ROOT,
                StagingTrustPolicy.STAGING_ROOT, StagingTrustPolicy.STAGING_ROOT).validate());
        assertThrows(IllegalStateException.class, () -> new StagingTrustPolicy(
                "test", "", StagingTrustPolicy.STAGING_ROOT,
                StagingTrustPolicy.STAGING_ROOT).validate());
        assertThrows(IllegalStateException.class, () -> new StagingTrustPolicy(
                "production", StagingTrustPolicy.STAGING_ROOT, "", "").validate());
    }
}
