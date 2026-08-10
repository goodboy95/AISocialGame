package com.aisocialgame.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeLogValueTest {

    @Test
    void fingerprintIsStableBoundedAndDoesNotExposeSourceValue() {
        String fingerprint = SafeLogValue.fingerprint("admin@example.test");

        assertThat(fingerprint).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(fingerprint).isEqualTo(SafeLogValue.fingerprint("admin@example.test"));
        assertThat(fingerprint).doesNotContain("admin", "example");
        assertThat(SafeLogValue.fingerprint(null)).isEqualTo("missing");
    }
}
