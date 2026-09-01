package com.aisocialgame.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiServiceTransportStartupGuardTest {
    @Test
    void acceptsCanonicalLocalAndTestTargetsWithTls() {
        MockEnvironment local = environment(
                AiServiceTransportPolicy.LOCAL_TARGET,
                "TLS",
                "");
        assertDoesNotThrow(() -> AiServiceTransportStartupGuard.validateBeforeServerCreation(
                local, raw("local", AiServiceTransportPolicy.LOCAL_TARGET,
                        "")));

        MockEnvironment test = environment(
                AiServiceTransportPolicy.TEST_TARGET, "TLS", AiServiceTransportPolicy.STAGING_TRUST);
        assertDoesNotThrow(() -> AiServiceTransportStartupGuard.validateBeforeServerCreation(
                test, raw("test", AiServiceTransportPolicy.TEST_TARGET,
                        AiServiceTransportPolicy.STAGING_TRUST)));
    }

    @Test
    void rejectsOldLocalPortAndFinalOrRawOverrides() {
        Map<String, String> raw = raw("local", AiServiceTransportPolicy.LOCAL_TARGET,
                "");
        assertThrows(IllegalStateException.class,
                () -> AiServiceTransportStartupGuard.validateBeforeServerCreation(
                        environment("static://localaiservice.testhut.top:443", "TLS",
                                ""), raw));

        Map<String, String> oldPort = new HashMap<>(raw);
        oldPort.put("AI_GRPC_ADDR", "static://localaiservice.testhut.top:443");
        assertThrows(IllegalStateException.class,
                () -> AiServiceTransportStartupGuard.validateBeforeServerCreation(
                        environment("static://localaiservice.testhut.top:443", "TLS",
                                ""), oldPort));

        assertThrows(IllegalStateException.class,
                () -> AiServiceTransportStartupGuard.validateBeforeServerCreation(
                        environment(AiServiceTransportPolicy.LOCAL_TARGET, "PLAINTEXT",
                                ""), raw));
    }

    @Test
    void rejectsMergedGlobalAndAiSpecificTlsOverrides() {
        Map<String, String> localRaw = raw("local", AiServiceTransportPolicy.LOCAL_TARGET,
                "");
        MockEnvironment globalAuthority = environment(
                AiServiceTransportPolicy.LOCAL_TARGET,
                "TLS",
                "")
                .withProperty("grpc.client.GLOBAL.security.authority-override", "attacker.invalid");
        assertThrows(IllegalStateException.class,
                () -> AiServiceTransportStartupGuard.validateBeforeServerCreation(globalAuthority, localRaw));

        MockEnvironment clientKey = environment(
                AiServiceTransportPolicy.LOCAL_TARGET,
                "TLS",
                "")
                .withProperty("grpc.client.ai.security.private-key", "file:/private/client.key");
        assertThrows(IllegalStateException.class,
                () -> AiServiceTransportStartupGuard.validateBeforeServerCreation(clientKey, localRaw));

        Map<String, String> testRaw = raw(
                "test", AiServiceTransportPolicy.TEST_TARGET, AiServiceTransportPolicy.STAGING_TRUST);
        MockEnvironment globalTrust = environment(AiServiceTransportPolicy.TEST_TARGET, "TLS", null)
                .withProperty("grpc.client.GLOBAL.security.trust-cert-collection", "file:/private/ca.crt");
        assertThrows(IllegalStateException.class,
                () -> AiServiceTransportStartupGuard.validateBeforeServerCreation(globalTrust, testRaw));
    }

    @Test
    void rejectsWrongTrustAndProductionWithoutSignedPreactivationAuthority() {
        Map<String, String> wrongTrust = raw("local", AiServiceTransportPolicy.LOCAL_TARGET,
                "file:/private/attacker-ca.crt");
        assertThrows(IllegalStateException.class,
                () -> AiServiceTransportStartupGuard.validateBeforeServerCreation(
                        environment(AiServiceTransportPolicy.LOCAL_TARGET, "TLS",
                                "file:/private/attacker-ca.crt"), wrongTrust));

        assertThrows(IllegalStateException.class,
                () -> AiServiceTransportStartupGuard.validateBeforeServerCreation(
                        environment(AiServiceTransportPolicy.PRODUCTION_TARGET, "TLS", null),
                        raw("production", AiServiceTransportPolicy.PRODUCTION_TARGET, "")));
    }

    private static MockEnvironment environment(String address, String negotiationType, String trust) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("grpc.client.ai.address", address)
                .withProperty("grpc.client.ai.negotiationType", negotiationType);
        if (trust != null) {
            environment.withProperty("grpc.client.ai.security.trust-cert-collection", trust);
        }
        return environment;
    }

    private static Map<String, String> raw(String runtimeEnvironment, String address, String trust) {
        return Map.of(
                "ENV", runtimeEnvironment,
                "AI_GRPC_ADDR", address,
                "AI_GRPC_NEGOTIATION_TYPE", "TLS",
                "GRPC_CLIENT_AI_SECURITY_TRUST_CERT_COLLECTION", trust);
    }
}
