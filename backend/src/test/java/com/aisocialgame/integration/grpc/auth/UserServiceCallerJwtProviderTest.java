package com.aisocialgame.integration.grpc.auth;

import com.aisocialgame.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceCallerJwtProviderTest {
    static final String SECRET = "aisocialgame-userservice-test-secret-32-bytes";

    @Test
    void issuesCanonicalHs256ClaimsAndRenewsInsideRefreshWindow() {
        AppProperties properties = validProperties();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AtomicInteger sequence = new AtomicInteger();
        UserServiceCallerJwtProvider provider = new UserServiceCallerJwtProvider(
                properties, clock, () -> "jti-" + sequence.incrementAndGet());

        String first = provider.currentToken();
        assertEquals(first, provider.currentToken());
        assertEquals(1, sequence.get());

        Jws<Claims> parsed = parse(first, clock.instant());
        assertEquals(SignatureAlgorithm.HS256.getValue(), parsed.getHeader().getAlgorithm());
        assertEquals("aisocialgame", parsed.getBody().getIssuer());
        assertEquals("aisocialgame", parsed.getBody().getSubject());
        assertEquals("aienie-userservice-grpc", parsed.getBody().getAudience());
        assertEquals("jti-1", parsed.getBody().getId());
        assertEquals(List.of(
                "user.auth.session.read",
                "user.directory.read",
                "user.ban.read",
                "user.ban.write"
        ), parsed.getBody().get("scopes", List.class));
        assertEquals(parsed.getBody().getIssuedAt(), parsed.getBody().getNotBefore());
        assertEquals(300L, Duration.between(
                parsed.getBody().getIssuedAt().toInstant(),
                parsed.getBody().getExpiration().toInstant()).toSeconds());

        clock.advance(Duration.ofSeconds(271));
        String renewed = provider.currentToken();
        assertNotEquals(first, renewed);
        assertEquals("jti-2", parse(renewed, clock.instant()).getBody().getId());
    }

    @Test
    void concurrentRefreshProducesOneSharedReplacementToken() throws Exception {
        AppProperties properties = validProperties();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T01:00:00Z"));
        AtomicInteger sequence = new AtomicInteger();
        UserServiceCallerJwtProvider provider = new UserServiceCallerJwtProvider(
                properties, clock, () -> "jti-" + sequence.incrementAndGet());
        String expiredSoon = provider.currentToken();
        clock.advance(Duration.ofSeconds(271));

        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch release = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                futures.add(executor.submit(() -> {
                    release.await();
                    return provider.currentToken();
                }));
            }
            release.countDown();
            List<String> values = new ArrayList<>();
            for (Future<String> future : futures) {
                values.add(future.get());
            }
            assertEquals(1L, values.stream().distinct().count());
            assertNotEquals(expiredSoon, values.getFirst());
            assertEquals(2, sequence.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsIdentityAudienceTtlScopeAndSecretViolationsWithoutLeakingSecret() {
        AppProperties wrongIdentity = validProperties();
        wrongIdentity.getExternal().getUserserviceJwt().setCallerId("another-caller");
        assertThrows(IllegalArgumentException.class,
                () -> new UserServiceCallerJwtProvider(wrongIdentity).currentToken());

        AppProperties wrongIssuer = validProperties();
        wrongIssuer.getExternal().getUserserviceJwt().setIssuer("another-issuer");
        assertThrows(IllegalArgumentException.class,
                () -> new UserServiceCallerJwtProvider(wrongIssuer).currentToken());

        AppProperties wrongAudience = validProperties();
        wrongAudience.getExternal().getUserserviceJwt().setAudience("wrong-audience");
        assertThrows(IllegalArgumentException.class,
                () -> new UserServiceCallerJwtProvider(wrongAudience).currentToken());

        AppProperties unsafeTtl = validProperties();
        unsafeTtl.getExternal().getUserserviceJwt().setTtlSeconds(901L);
        assertThrows(IllegalArgumentException.class,
                () -> new UserServiceCallerJwtProvider(unsafeTtl).currentToken());

        AppProperties expandedScope = validProperties();
        expandedScope.getExternal().getUserserviceJwt().setScopes(
                "user.auth.session.read,user.directory.read,user.ban.read,user.ban.write,user.preference.read");
        assertThrows(IllegalArgumentException.class,
                () -> new UserServiceCallerJwtProvider(expandedScope).currentToken());

        for (String invalidSecret : List.of(
                "too-short-and-private",
                "REPLACE_WITH_A_PRIVATE_USERSERVICE_SECRET",
                "private-changeme-userservice-secret-32-bytes",
                " " + SECRET,
                SECRET + " ",
                SECRET + "\n"
        )) {
            AppProperties invalid = validProperties();
            invalid.getExternal().getUserserviceJwt().setSecret(invalidSecret);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new UserServiceCallerJwtProvider(invalid).currentToken());
            assertFalse(error.getMessage().contains(invalidSecret));
        }
    }

    static AppProperties validProperties() {
        AppProperties properties = new AppProperties();
        AppProperties.UserServiceJwt jwt = properties.getExternal().getUserserviceJwt();
        jwt.setCallerId("aisocialgame");
        jwt.setIssuer("aisocialgame");
        jwt.setSecret(SECRET);
        jwt.setAudience("aienie-userservice-grpc");
        jwt.setTtlSeconds(300L);
        jwt.setScopes("user.auth.session.read,user.directory.read,user.ban.read,user.ban.write");
        return properties;
    }

    static Jws<Claims> parse(String token, Instant verificationInstant) {
        return Jwts.parserBuilder()
                .requireIssuer("aisocialgame")
                .requireSubject("aisocialgame")
                .requireAudience("aienie-userservice-grpc")
                .setClock(() -> Date.from(verificationInstant))
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token);
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported by this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
