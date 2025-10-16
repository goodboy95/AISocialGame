package com.aisocialgame.backend.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aisocialgame.backend.config.JwtProperties;
import com.aisocialgame.backend.dto.AuthDtos;
import com.aisocialgame.backend.entity.RefreshToken;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.repository.RefreshTokenRepository;
import com.aisocialgame.backend.security.AccountUserDetails;
import com.aisocialgame.backend.security.JwtService;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    public AuthDtos.TokenResponse login(String username, String password) {
        log.debug("Attempting login for user '{}'", username);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        AccountUserDetails userDetails = (AccountUserDetails) authentication.getPrincipal();
        UserAccount user = userDetails.getUser();
        String accessToken = jwtService.createAccessToken(user);
        String refresh = createRefreshToken(user);
        log.debug("User '{}' authenticated successfully", username);
        return new AuthDtos.TokenResponse(accessToken, refresh);
    }

    public String createRefreshToken(UserAccount user) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenTtl()));
        refreshTokenRepository.save(token);
        log.debug("Created refresh token for user {} expiring at {}", user.getId(), token.getExpiresAt());
        return token.getToken();
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("刷新令牌无效"));
        String accessToken = jwtService.createAccessToken(stored.getUser());
        return new AuthDtos.TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
    }

    public UserAccount currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountUserDetails userDetails)) {
            return null;
        }
        return userDetails.getUser();
    }
}
