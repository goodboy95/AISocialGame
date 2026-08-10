package com.aisocialgame;

import com.aisocialgame.adminauth.AdminAuthCrypto;
import com.aisocialgame.adminauth.AdminAuthPolicy;
import com.aisocialgame.adminauth.AdminAuthStore;
import com.aisocialgame.adminauth.AdminRateLimiter;
import com.aisocialgame.config.AppProperties;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.service.AdminAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceTest {

    @Test
    void localPasswordModeIssuesServerSideSessionWithoutTotpChallenge() {
        Fixture fixture = fixture(new AdminAuthPolicy("local", "password"), false);

        AdminAuthService.LoginResult result = fixture.service.login("admin", "correct-password", "127.0.0.1");

        assertEquals("AUTHENTICATED", result.state());
        assertNotNull(result.sessionToken());
        verify(fixture.store).insertSession(any(), any(), any(), any(), any(), any(), any(),
                anyLong(), any(), any(), any(), any());
    }

    @Test
    void totpModeRequiresPasswordFirstAndCreatesTotpChallenge() {
        Fixture fixture = fixture(new AdminAuthPolicy("local", "totp"), true);

        AdminAuthService.LoginResult result = fixture.service.login("admin", "correct-password", "127.0.0.1");

        assertEquals("TOTP_REQUIRED", result.state());
        assertNotNull(result.challengeId());
        verify(fixture.store).insertChallenge(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void wrongPasswordNeverCreatesSession() {
        Fixture fixture = fixture(new AdminAuthPolicy("local", "password"), false);
        assertThrows(ApiException.class,
                () -> fixture.service.login("admin", "wrong-password", "127.0.0.1"));
    }

    private Fixture fixture(AdminAuthPolicy policy, boolean enrolled) {
        AppProperties properties = new AppProperties();
        properties.getAdmin().setUsername("admin");
        properties.getAdmin().setPasswordHash(new BCryptPasswordEncoder(4).encode("correct-password"));
        properties.getAdmin().setTotpEncryptionKeys("v1:" + Base64.getEncoder().encodeToString(new byte[32]));
        properties.getAdmin().setTotpActiveKeyVersion("v1");
        AdminAuthStore store = mock(AdminAuthStore.class);
        when(store.credentialExists(any())).thenReturn(enrolled);
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        when(limiter.allow(any(), anyInt(), any(Duration.class))).thenReturn(true);
        AdminAuthService service = new AdminAuthService(properties, policy, store,
                new AdminAuthCrypto(properties), limiter);
        return new Fixture(service, store);
    }

    private record Fixture(AdminAuthService service, AdminAuthStore store) {
    }
}
