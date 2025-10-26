package com.aisocialgame.backend.security;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aisocialgame.backend.config.JwtProperties;
import com.aisocialgame.backend.entity.UserAccount;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtService {

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getAccessTokenTtl());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("username", user.getUsername())
                .claim("display_name", user.getDisplayName())
                .claim("is_admin", user.isAdmin())
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes()))
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes()))
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
