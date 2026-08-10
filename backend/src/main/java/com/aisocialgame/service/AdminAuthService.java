package com.aisocialgame.service;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.logging.SafeLogThrowable;
import com.aisocialgame.logging.SafeLogValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AdminAuthService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final AppProperties appProperties;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final String adminSessionKeyPrefix;

    @Autowired
    public AdminAuthService(AppProperties appProperties, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.appProperties = appProperties;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.adminSessionKeyPrefix = appProperties.getProjectKey() + ":admin:session:";
    }

    public AdminAuthService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.redisTemplate = null;
        this.adminSessionKeyPrefix = appProperties.getProjectKey() + ":admin:session:";
    }

    public String login(String username, String password) {
        String expectedUsername = appProperties.getAdmin().getUsername();
        String expectedPassword = appProperties.getAdmin().getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)
                || !username.equals(expectedUsername) || !password.equals(expectedPassword)) {
            log.warn("Admin login rejected actorFingerprint={} reason=INVALID_CREDENTIALS", auditActorFingerprint(username));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "管理员账号或密码错误");
        }
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofHours(Math.max(1, appProperties.getAdmin().getTokenTtlHours()));
        String sessionStore = redisTemplate != null ? "redis" : "memory";
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(adminSessionKey(token), username, ttl);
            } else {
                sessions.put(token, new Session(username, Instant.now().plus(ttl)));
            }
        } catch (RuntimeException ex) {
            log.error("Admin login session creation failed actorFingerprint={} sessionStore={} errorType={}",
                    auditActorFingerprint(username), sessionStore, ex.getClass().getSimpleName(),
                    SafeLogThrowable.stackOnly(ex));
            throw ex;
        }
        log.info("Admin login succeeded actorFingerprint={} sessionStore={} ttlHours={}",
                auditActorFingerprint(username), sessionStore, ttl.toHours());
        return token;
    }

    public String requireAdmin(String token) {
        String sessionStore = redisTemplate != null ? "redis" : "memory";
        if (!StringUtils.hasText(token)) {
            log.warn("Admin session rejected reasonCode=MISSING_TOKEN sessionStore={}", sessionStore);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "缺少管理员令牌");
        }
        if (redisTemplate != null) {
            String username;
            try {
                username = redisTemplate.opsForValue().get(adminSessionKey(token));
            } catch (RuntimeException ex) {
                log.error("Admin session validation failed reasonCode=SESSION_STORE_ERROR sessionStore=redis errorType={}",
                        ex.getClass().getSimpleName(), SafeLogThrowable.stackOnly(ex));
                throw ex;
            }
            if (!StringUtils.hasText(username)) {
                log.warn("Admin session rejected reasonCode=EXPIRED_OR_UNKNOWN_SESSION sessionStore=redis");
                throw new ApiException(HttpStatus.UNAUTHORIZED, "管理员登录已过期");
            }
            return username;
        }
        Session session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            log.warn("Admin session rejected reasonCode=EXPIRED_OR_UNKNOWN_SESSION sessionStore=memory");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "管理员登录已过期");
        }
        return session.username();
    }

    public String getDisplayName() {
        return appProperties.getAdmin().getDisplayName();
    }

    @Scheduled(fixedDelayString = "${app.admin.session-cleanup-interval-ms:300000}")
    public void cleanupExpiredSessions() {
        if (redisTemplate != null || sessions.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        AtomicInteger removedCount = new AtomicInteger();
        sessions.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiresAt().isBefore(now);
            if (expired) {
                removedCount.incrementAndGet();
            }
            return expired;
        });
        if (removedCount.get() > 0) {
            log.info("Admin session cleanup completed sessionStore=memory removedCount={}", removedCount.get());
        }
    }

    private String adminSessionKey(String token) {
        return adminSessionKeyPrefix + token;
    }

    private String auditActorFingerprint(String username) {
        return SafeLogValue.fingerprint(StringUtils.hasText(username) ? username.trim() : null);
    }

    private record Session(String username, Instant expiresAt) {
    }
}
