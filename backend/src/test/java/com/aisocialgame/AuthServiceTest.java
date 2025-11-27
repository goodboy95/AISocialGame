package com.aisocialgame;

import com.aisocialgame.exception.ApiException;
import com.aisocialgame.model.User;
import com.aisocialgame.service.AuthService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest(classes = AiSocialGameApplication.class)
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Test
    void registerLoginAndAuthenticateWithToken() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        User created = authService.register(email, "StrongPass!23", "测试用户");
        Assertions.assertNotNull(created.getId());

        String token = authService.issueToken(created);
        Assertions.assertNotNull(token);
        Assertions.assertEquals(created.getId(), authService.authenticate(token).getId());

        User login = authService.login(email, "StrongPass!23");
        Assertions.assertEquals(created.getId(), login.getId());
    }

    @Test
    void duplicateEmailShouldFail() {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        authService.register(email, "password", "用户A");
        Assertions.assertThrows(ApiException.class, () -> authService.register(email, "password", "用户B"));
    }
}
