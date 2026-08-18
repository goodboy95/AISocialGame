package com.aisocialgame.integration.grpc.auth;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.config.UserServiceJwtConfigurationValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Issues short-lived, caller-scoped JWTs for protected UserService gRPC calls. */
@Component
public class UserServiceCallerJwtProvider {
    private final AppProperties appProperties;
    private final Clock clock;
    private final Supplier<String> jtiSupplier;
    private final Object monitor = new Object();
    private volatile CachedToken cachedToken;

    @Autowired
    public UserServiceCallerJwtProvider(AppProperties appProperties) {
        this(appProperties, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    UserServiceCallerJwtProvider(AppProperties appProperties,
                                 Clock clock,
                                 Supplier<String> jtiSupplier) {
        this.appProperties = Objects.requireNonNull(appProperties);
        this.clock = Objects.requireNonNull(clock);
        this.jtiSupplier = Objects.requireNonNull(jtiSupplier);
    }

    public String currentToken() {
        Configuration configuration = snapshotAndValidate();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        long refreshWindowSeconds = Math.min(30L, Math.max(5L, configuration.ttlSeconds / 5L));
        CachedToken observed = cachedToken;
        if (usable(observed, configuration, now, refreshWindowSeconds)) {
            return observed.value;
        }

        synchronized (monitor) {
            observed = cachedToken;
            if (usable(observed, configuration, now, refreshWindowSeconds)) {
                return observed.value;
            }
            Instant expiresAt = now.plusSeconds(configuration.ttlSeconds);
            String token = Jwts.builder()
                    .setIssuer(configuration.issuer)
                    .setSubject(configuration.callerId)
                    .setAudience(configuration.audience)
                    .setIssuedAt(Date.from(now))
                    .setNotBefore(Date.from(now))
                    .setExpiration(Date.from(expiresAt))
                    .setId(requireJti(jtiSupplier.get()))
                    .claim("scopes", UserServiceJwtConfigurationValidator.REQUIRED_SCOPES)
                    .signWith(
                            Keys.hmacShaKeyFor(configuration.secret.getBytes(StandardCharsets.UTF_8)),
                            SignatureAlgorithm.HS256
                    )
                    .compact();
            cachedToken = new CachedToken(configuration, token, expiresAt);
            return token;
        }
    }

    private boolean usable(CachedToken token,
                           Configuration configuration,
                           Instant now,
                           long refreshWindowSeconds) {
        return token != null
                && token.configuration.sameAs(configuration)
                && token.expiresAt.isAfter(now.plusSeconds(refreshWindowSeconds));
    }

    private Configuration snapshotAndValidate() {
        AppProperties.UserServiceJwt source = appProperties.getExternal().getUserserviceJwt();
        UserServiceJwtConfigurationValidator.validate(source);
        return new Configuration(
                source.getCallerId(),
                source.getIssuer(),
                source.getSecret(),
                source.getAudience(),
                source.getTtlSeconds()
        );
    }

    private String requireJti(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalStateException("Unable to issue UserService caller JWT with a valid jti");
        }
        return normalized;
    }

    private record Configuration(String callerId,
                                 String issuer,
                                 String secret,
                                 String audience,
                                 long ttlSeconds) {
        private boolean sameAs(Configuration other) {
            return other != null
                    && ttlSeconds == other.ttlSeconds
                    && callerId.equals(other.callerId)
                    && issuer.equals(other.issuer)
                    && secret.equals(other.secret)
                    && audience.equals(other.audience);
        }

        @Override
        public String toString() {
            return "Configuration[callerId=" + callerId
                    + ", issuer=" + issuer
                    + ", secret=<redacted>, audience=" + audience
                    + ", ttlSeconds=" + ttlSeconds + "]";
        }
    }

    private record CachedToken(Configuration configuration, String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "CachedToken[configuration=" + configuration
                    + ", value=<redacted>, expiresAt=" + expiresAt + "]";
        }
    }
}
