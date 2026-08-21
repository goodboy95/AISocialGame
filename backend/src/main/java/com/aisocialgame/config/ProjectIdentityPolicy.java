package com.aisocialgame.config;

import java.util.Map;

/** Canonical application identity shared by local state and external requests. */
final class ProjectIdentityPolicy {
    static final String PROJECT_KEY = AppProperties.CANONICAL_PROJECT_KEY;

    private ProjectIdentityPolicy() {
    }

    static void validateRaw(Map<String, String> environment) {
        if (environment == null || !PROJECT_KEY.equals(environment.get("APP_PROJECT_KEY"))) {
            throw new IllegalStateException("APP_PROJECT_KEY must be exactly " + PROJECT_KEY);
        }
    }
}
