package com.aisocialgame;

import com.aisocialgame.dto.AuthResponse;
import com.aisocialgame.integration.grpc.client.UserGrpcClient;
import com.aisocialgame.integration.grpc.dto.AuthSessionResult;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.integration.grpc.dto.ExternalUserProfile;
import com.aisocialgame.model.User;
import com.aisocialgame.service.AuthService;
import com.aisocialgame.service.BalanceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

@SpringBootTest(classes = AiSocialGameApplication.class)
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @MockBean
    private UserGrpcClient userGrpcClient;

    @MockBean
    private BalanceService balanceService;

    @Test
    void registerLoginAndAuthenticateWithToken() {
        ExternalUserProfile profile = new ExternalUserProfile(
                2001L,
                "tester01",
                "tester01@example.com",
                "https://avatar.example/u1.png",
                true,
                null,
                Instant.now()
        );
        Mockito.when(userGrpcClient.register(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new AuthSessionResult(profile, "remote-access-1", "session-1"));
        Mockito.when(userGrpcClient.login(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new AuthSessionResult(profile, "remote-access-2", "session-2"));
        Mockito.when(userGrpcClient.validateSession(Mockito.eq(2001L), Mockito.anyString()))
                .thenReturn(profile);
        Mockito.when(balanceService.getUserBalance(Mockito.any(User.class)))
                .thenReturn(BalanceSnapshot.empty());

        AuthResponse registerResponse = authService.register("tester01", "tester01@example.com", "StrongPass!23", "测试用户", "127.0.0.1", "junit");
        Assertions.assertNotNull(registerResponse.getToken());
        Assertions.assertEquals(2001L, registerResponse.getUser().getExternalUserId());

        User authenticated = authService.authenticate(registerResponse.getToken());
        Assertions.assertNotNull(authenticated);
        Assertions.assertEquals("tester01", authenticated.getUsername());

        AuthResponse loginResponse = authService.login("tester01", "StrongPass!23", "127.0.0.1", "junit");
        Assertions.assertNotNull(loginResponse.getToken());
        Assertions.assertEquals("测试用户", loginResponse.getUser().getNickname());
    }

    @Test
    void loginWithEmailShouldMapToStoredUsername() {
        ExternalUserProfile profile = new ExternalUserProfile(
                2002L,
                "email_user",
                "email_user@example.com",
                "https://avatar.example/u2.png",
                true,
                null,
                Instant.now()
        );
        Mockito.when(userGrpcClient.register(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new AuthSessionResult(profile, "remote-access", "session-3"));
        Mockito.when(userGrpcClient.login(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new AuthSessionResult(profile, "remote-access", "session-4"));
        Mockito.when(balanceService.getUserBalance(Mockito.any(User.class)))
                .thenReturn(BalanceSnapshot.empty());

        authService.register("email_user", "email_user@example.com", "Password!123", "邮箱用户", "127.0.0.1", "junit");
        authService.login("email_user@example.com", "Password!123", "127.0.0.1", "junit");

        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(userGrpcClient, Mockito.atLeastOnce())
                .login(usernameCaptor.capture(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Assertions.assertTrue(usernameCaptor.getAllValues().contains("email_user"));
    }

    @Test
    void authenticateShouldReturnNullWhenSessionExpired() {
        ExternalUserProfile profile = new ExternalUserProfile(
                2003L,
                "expired_user",
                "expired_user@example.com",
                "https://avatar.example/u3.png",
                true,
                null,
                Instant.now()
        );
        Mockito.when(userGrpcClient.register(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new AuthSessionResult(profile, "remote-access", "session-5"));
        Mockito.when(balanceService.getUserBalance(Mockito.any(User.class)))
                .thenReturn(BalanceSnapshot.empty());
        Mockito.when(userGrpcClient.validateSession(Mockito.eq(2003L), Mockito.anyString()))
                .thenReturn(null);

        AuthResponse response = authService.register("expired_user", "expired_user@example.com", "Password!123", "过期用户", "127.0.0.1", "junit");
        Assertions.assertNull(authService.authenticate(response.getToken()));
    }
}
