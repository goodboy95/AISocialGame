package com.aisocialgame;

import com.aisocialgame.dto.AuthResponse;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.consul.ConsulHttpServiceDiscovery;
import com.aisocialgame.integration.grpc.client.UserGrpcClient;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.integration.grpc.dto.ExternalUserProfile;
import com.aisocialgame.model.User;
import com.aisocialgame.service.AuthService;
import com.aisocialgame.service.BalanceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
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

    @MockBean
    private ConsulHttpServiceDiscovery consulHttpServiceDiscovery;

    @Test
    void ssoCallbackAndAuthenticateShouldSucceed() {
        ExternalUserProfile profile = new ExternalUserProfile(
                2001L,
                "tester01",
                "tester01@example.com",
                "https://avatar.example/u1.png",
                true,
                null,
                Instant.now()
        );
        Mockito.when(userGrpcClient.validateSession(Mockito.eq(2001L), Mockito.anyString()))
                .thenReturn(profile);
        Mockito.when(balanceService.getUserBalance(Mockito.any(User.class)))
                .thenReturn(BalanceSnapshot.empty());

        AuthResponse response = authService.ssoCallback(2001L, "测试用户", "session-1", "access-1");
        Assertions.assertNotNull(response.getToken());
        Assertions.assertEquals(2001L, response.getUser().getExternalUserId());

        User authenticated = authService.authenticate(response.getToken());
        Assertions.assertNotNull(authenticated);
        Assertions.assertEquals("tester01", authenticated.getUsername());
    }

    @Test
    void ssoCallbackShouldFailWhenSessionInvalid() {
        Mockito.when(userGrpcClient.validateSession(Mockito.eq(3001L), Mockito.anyString()))
                .thenReturn(null);

        ApiException ex = Assertions.assertThrows(ApiException.class,
                () -> authService.ssoCallback(3001L, "tester", "expired-session", "token"));
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void buildSsoLoginRedirectUrlShouldContainRedirectAndState() {
        Mockito.when(consulHttpServiceDiscovery.resolveHttpAddress(Mockito.anyString()))
                .thenReturn("http://user-service:19090");

        String redirectUrl = authService.buildSsoLoginRedirectUrl("state_token_123456");
        Assertions.assertTrue(redirectUrl.startsWith("http://user-service:19090/sso/login"));
        Assertions.assertTrue(redirectUrl.contains("redirect="));
        Assertions.assertTrue(redirectUrl.contains("state=state_token_123456"));
    }

    @Test
    void buildSsoRegisterRedirectUrlShouldContainRedirectAndState() {
        Mockito.when(consulHttpServiceDiscovery.resolveHttpAddress(Mockito.anyString()))
                .thenReturn("http://user-service:19090");

        String redirectUrl = authService.buildSsoRegisterRedirectUrl("state_token_abcdef");
        Assertions.assertTrue(redirectUrl.startsWith("http://user-service:19090/register"));
        Assertions.assertTrue(redirectUrl.contains("redirect="));
        Assertions.assertTrue(redirectUrl.contains("state=state_token_abcdef"));
    }

    @Test
    void buildSsoRedirectUrlShouldFailWhenStateInvalid() {
        ApiException ex = Assertions.assertThrows(ApiException.class,
                () -> authService.buildSsoLoginRedirectUrl("bad"));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
