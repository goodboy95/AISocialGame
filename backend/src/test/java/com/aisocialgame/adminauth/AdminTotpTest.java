package com.aisocialgame.adminauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminTotpTest {
    @Test
    void acceptsClockSkewAndReturnsTheActualMatchedTimestep() {
        String secret = "JBSWY3DPEHPK3PXP";
        long current = 2_000_000L;
        String nextStepCode = AdminTotp.code(secret, current + 1);

        assertEquals(current + 1,
                AdminTotp.matchingTimestep(secret, nextStepCode, current * 30, -1));
    }

    @Test
    void rejectsAPreviouslyAcceptedTimestep() {
        String secret = "JBSWY3DPEHPK3PXP";
        long current = 2_000_000L;
        String code = AdminTotp.code(secret, current);

        assertEquals(-1, AdminTotp.matchingTimestep(secret, code, current * 30, current));
    }
}
