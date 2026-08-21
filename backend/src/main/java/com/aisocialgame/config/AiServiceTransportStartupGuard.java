package com.aisocialgame.config;

import jakarta.annotation.PostConstruct;
import net.devh.boot.grpc.client.config.GrpcChannelProperties;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** Locks final ai-service address and negotiation type to the canonical raw environment. */
@Component
public final class AiServiceTransportStartupGuard {
    private final ConfigurableEnvironment environment;
    private final ObjectProvider<RuntimeProfileTestAuthorization> testAuthorization;

    public AiServiceTransportStartupGuard(ConfigurableEnvironment environment,
                                          ObjectProvider<RuntimeProfileTestAuthorization> testAuthorization) {
        this.environment = environment;
        this.testAuthorization = testAuthorization;
    }

    @PostConstruct
    public void validate() {
        if (testAuthorization.getIfAvailable() == null) {
            validateBeforeServerCreation(environment, System.getenv());
        }
    }

    /** Runs from the packaged application's initializer, before the web server is created. */
    public static void validateBeforeServerCreation(ConfigurableEnvironment environment,
                                                    Map<String, String> rawEnvironment) {
        AiServiceTransportPolicy.Expected expected = AiServiceTransportPolicy.validateRaw(rawEnvironment);
        GrpcChannelsProperties channels = Binder.get(environment)
                .bind("grpc", Bindable.of(GrpcChannelsProperties.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Final ai-service gRPC transport configuration is unavailable"));
        GrpcChannelProperties channel = channels.getChannel("ai");
        String address = channel.getAddress() == null ? "" : channel.getAddress().toString();
        String negotiationType = channel.getNegotiationType() == null
                ? "" : channel.getNegotiationType().name();
        requireBound(rawEnvironment, "AI_GRPC_ADDR", address);
        requireBound(rawEnvironment, "AI_GRPC_NEGOTIATION_TYPE", negotiationType);
        if (!expected.target().equals(address) || !"TLS".equals(negotiationType)) {
            throw new IllegalStateException("Final ai-service gRPC target or TLS mode is not canonical");
        }
        String directTrust = Binder.get(environment)
                .bind("grpc.client.ai.security.trust-cert-collection", String.class)
                .orElse("");
        requireBound(rawEnvironment, "GRPC_CLIENT_AI_SECURITY_TRUST_CERT_COLLECTION", directTrust);
        if (!expected.trust().equals(directTrust)) {
            throw new IllegalStateException("Final ai-service gRPC trust source is not canonical");
        }
        GrpcChannelProperties.Security security = channel.getSecurity();
        Resource finalTrust = security.getTrustCertCollection();
        if (directTrust.isEmpty() ? finalTrust != null : !sameResource(finalTrust, directTrust)) {
            throw new IllegalStateException("Final ai-service CA binding is internally inconsistent");
        }
        if (security.isClientAuthEnabled()
                || security.getCertificateChain() != null
                || security.getPrivateKey() != null
                || hasText(security.getPrivateKeyPassword())
                || !"AUTODETECT".equals(security.getKeyStoreFormat())
                || security.getKeyStore() != null
                || hasText(security.getKeyStorePassword())
                || !"AUTODETECT".equals(security.getTrustStoreFormat())
                || security.getTrustStore() != null
                || hasText(security.getTrustStorePassword())
                || hasText(security.getAuthorityOverride())
                || (security.getCiphers() != null && !security.getCiphers().isEmpty())
                || (security.getProtocols() != null && security.getProtocols().length != 0)) {
            throw new IllegalStateException("Non-canonical ai-service TLS security override is forbidden");
        }
    }

    private static boolean sameResource(Resource finalResource, String expectedLocation) {
        if (finalResource == null) {
            return false;
        }
        try {
            Resource expected = new DefaultResourceLoader().getResource(expectedLocation);
            return finalResource.getURI().normalize().equals(expected.getURI().normalize());
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static void requireBound(Map<String, String> rawEnvironment,
                                     String name,
                                     String finalValue) {
        String raw = rawEnvironment == null ? null : rawEnvironment.get(name);
        if (raw == null || !raw.equals(finalValue)) {
            throw new IllegalStateException(
                    "Final ai-service transport configuration does not match its canonical environment source: "
                            + name);
        }
    }
}
