package com.aisocialgame.service;

import com.aisocialgame.dto.AuthResponse;
import com.aisocialgame.dto.AuthUserView;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.grpc.client.UserGrpcClient;
import com.aisocialgame.integration.grpc.dto.AuthSessionResult;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.integration.grpc.dto.ExternalUserProfile;
import com.aisocialgame.model.User;
import com.aisocialgame.repository.UserRepository;
import com.aisocialgame.service.token.TokenStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class AuthService {
    private static final String EXTERNAL_PASSWORD_MARKER = "{external}";

    private final UserRepository userRepository;
    private final TokenStore tokenStore;
    private final UserGrpcClient userGrpcClient;
    private final BalanceService balanceService;

    public AuthService(UserRepository userRepository,
                       TokenStore tokenStore,
                       UserGrpcClient userGrpcClient,
                       BalanceService balanceService) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
        this.userGrpcClient = userGrpcClient;
        this.balanceService = balanceService;
    }

    public AuthResponse register(String username,
                                 String email,
                                 String password,
                                 String nickname,
                                 String ipAddress,
                                 String userAgent) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedNickname = nickname == null ? "" : nickname.trim();
        String resolvedUsername = resolveRegisterUsername(username, normalizedEmail);
        String avatar = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + normalizedNickname.replace(" ", "");
        AuthSessionResult session = userGrpcClient.register(
                resolvedUsername,
                normalizedEmail,
                password,
                normalizedNickname,
                avatar,
                ipAddress,
                userAgent
        );

        User localUser = upsertLocalUser(session, normalizedNickname);
        String token = issueToken(localUser);
        return new AuthResponse(token, buildUserView(localUser));
    }

    public AuthResponse login(String account, String password, String ipAddress, String userAgent) {
        String resolvedUsername = resolveLoginUsername(account);
        AuthSessionResult session = userGrpcClient.login(resolvedUsername, password, ipAddress, userAgent);
        User localUser = upsertLocalUser(session, resolvedUsername);
        String token = issueToken(localUser);
        return new AuthResponse(token, buildUserView(localUser));
    }

    public String issueToken(User user) {
        String token = UUID.randomUUID().toString();
        tokenStore.store(token, user.getId());
        return token;
    }

    public User authenticate(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String localUserId = tokenStore.getUserId(token);
        if (!StringUtils.hasText(localUserId)) {
            return null;
        }
        User localUser = userRepository.findById(localUserId).orElse(null);
        if (localUser == null || localUser.getExternalUserId() == null || !StringUtils.hasText(localUser.getSessionId())) {
            return null;
        }
        ExternalUserProfile profile = userGrpcClient.validateSession(localUser.getExternalUserId(), localUser.getSessionId());
        if (profile == null) {
            tokenStore.revoke(token);
            return null;
        }
        mergeExternalProfile(localUser, profile, localUser.getNickname());
        return userRepository.save(localUser);
    }

    public AuthUserView currentUserView(String token) {
        User user = authenticate(token);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return buildUserView(user);
    }

    private User upsertLocalUser(AuthSessionResult session, String defaultNickname) {
        ExternalUserProfile profile = session.user();
        User local = userRepository.findByExternalUserId(profile.userId())
                .orElseGet(() -> new User());
        mergeExternalProfile(local, profile, defaultNickname);
        local.setSessionId(session.sessionId());
        local.setAccessToken(session.accessToken());
        return userRepository.save(local);
    }

    private void mergeExternalProfile(User local, ExternalUserProfile profile, String defaultNickname) {
        if (!StringUtils.hasText(local.getId())) {
            local.setId(UUID.randomUUID().toString());
        }
        local.setExternalUserId(profile.userId());
        local.setUsername(profile.username());
        if (StringUtils.hasText(profile.email())) {
            local.setEmail(profile.email().trim().toLowerCase(Locale.ROOT));
        } else if (!StringUtils.hasText(local.getEmail())) {
            local.setEmail(profile.userId() + "@placeholder.local");
        }
        local.setAvatar(StringUtils.hasText(profile.avatarUrl()) ? profile.avatarUrl() : local.getAvatar());
        if (!StringUtils.hasText(local.getNickname())) {
            local.setNickname(StringUtils.hasText(defaultNickname) ? defaultNickname : fallbackNickname(profile.username()));
        }
        if (!StringUtils.hasText(local.getPassword())) {
            local.setPassword(EXTERNAL_PASSWORD_MARKER);
        }
        if (local.getLevel() <= 0) {
            local.setLevel(1);
        }
    }

    private AuthUserView buildUserView(User user) {
        BalanceSnapshot snapshot;
        try {
            snapshot = balanceService.getUserBalance(user);
        } catch (Exception ignored) {
            snapshot = BalanceSnapshot.empty();
        }
        return new AuthUserView(user, snapshot);
    }

    private String resolveLoginUsername(String account) {
        if (!StringUtils.hasText(account)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        String normalized = account.trim();
        if (normalized.contains("@")) {
            return userRepository.findByEmail(normalized.toLowerCase(Locale.ROOT))
                    .map(User::getUsername)
                    .filter(StringUtils::hasText)
                    .orElse(normalized);
        }
        return normalized;
    }

    private String resolveRegisterUsername(String username, String email) {
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        int atIndex = email.indexOf("@");
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String fallbackNickname(String username) {
        if (StringUtils.hasText(username)) {
            return username;
        }
        return "玩家" + UUID.randomUUID().toString().substring(0, 6);
    }
}
