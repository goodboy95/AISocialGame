package com.aisocialgame.adminauth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminAuthPolicyTest {
    @Test
    void acceptsOnlyFourExactCombinations() {
        assertDoesNotThrow(() -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "local", "AUTH_MODE", "password")));
        assertDoesNotThrow(() -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "local", "AUTH_MODE", "totp")));
        assertDoesNotThrow(() -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "test", "AUTH_MODE", "totp")));
        assertDoesNotThrow(() -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "production", "AUTH_MODE", "totp")));
    }

    @Test
    void rejectsMissingWhitespaceQuotedCaseChangedAndUnsafeCombinations() {
        assertThrows(IllegalStateException.class, () -> AdminAuthPolicy.fromEnvironment(Map.of()));
        assertThrows(IllegalStateException.class, () -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "local ", "AUTH_MODE", "totp")));
        assertThrows(IllegalStateException.class, () -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "\"local\"", "AUTH_MODE", "totp")));
        assertThrows(IllegalStateException.class, () -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "LOCAL", "AUTH_MODE", "totp")));
        assertThrows(IllegalStateException.class, () -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "test", "AUTH_MODE", "password")));
        assertThrows(IllegalStateException.class, () -> AdminAuthPolicy.fromEnvironment(Map.of("ENV", "production", "AUTH_MODE", "password")));
    }
}
