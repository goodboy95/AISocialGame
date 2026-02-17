package com.aisocialgame.service;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAuthService {
    private final AppProperties appProperties;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String login(String username, String password) {
        String expectedUsername = appProperties.getAdmin().getUsername();
        String expectedPassword = appProperties.getAdmin().getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)
                || !username.equals(expectedUsername) || !password.equals(expectedPassword)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "管理员账号或密码错误");
        }
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofHours(Math.max(1, appProperties.getAdmin().getTokenTtlHours()));
        sessions.put(token, new Session(username, Instant.now().plus(ttl)));
        return token;
    }

    public String requireAdmin(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "缺少管理员令牌");
        }
        Session session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "管理员登录已过期");
        }
        return session.username();
    }

    public String getDisplayName() {
        return appProperties.getAdmin().getDisplayName();
    }

    private record Session(String username, Instant expiresAt) {
    }
}
