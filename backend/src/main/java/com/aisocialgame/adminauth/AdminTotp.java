package com.aisocialgame.adminauth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class AdminTotp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private AdminTotp() {
    }

    public static long matchingTimestep(String secret, String code, long nowSeconds, long lastAccepted) {
        String normalized = code == null ? "" : code.replaceAll("\\s+", "");
        if (!normalized.matches("\\d{6}")) {
            return -1;
        }
        long current = Math.floorDiv(nowSeconds, 30);
        for (long candidate = current - 1; candidate <= current + 1; candidate++) {
            if (candidate <= lastAccepted) {
                continue;
            }
            if (MessageDigest.isEqual(code(secret, candidate).getBytes(StandardCharsets.US_ASCII),
                    normalized.getBytes(StandardCharsets.US_ASCII))) {
                return candidate;
            }
        }
        return -1;
    }

    static String code(String secret, long timestep) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(timestep).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate TOTP", ex);
        }
    }

    private static byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char ch : normalized.toCharArray()) {
            int index = ALPHABET.indexOf(ch);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid Base32 value");
            }
            buffer = (buffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }
}
