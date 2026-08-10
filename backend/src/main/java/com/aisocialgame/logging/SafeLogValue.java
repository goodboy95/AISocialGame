package com.aisocialgame.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable pseudonymous identifiers for correlating sensitive values in logs. */
public final class SafeLogValue {

    private SafeLogValue() {
    }

    public static String fingerprint(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "missing";
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
