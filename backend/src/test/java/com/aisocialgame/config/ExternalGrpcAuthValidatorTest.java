package com.aisocialgame.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalGrpcAuthValidatorTest {
    private static final String SECRET = "aisocialgame-userservice-test-secret-32-bytes";

    @Test
    void rejectsMissingExternalAuthenticationConfiguration() {
        AppProperties properties = new AppProperties();
        properties.setExternal(null);

        assertThrows(IllegalStateException.class,
                () -> new ExternalGrpcAuthValidator(properties).validate());
    }

    @Test
    void acceptsCanonicalCallerConfiguration() {
        AppProperties properties = validProperties();
        assertDoesNotThrow(() -> new ExternalGrpcAuthValidator(properties).validate());
    }

    @Test
    void rejectsLegacyStaticTokenEvenWhenExternalAuthFlagIsDisabled() {
        AppProperties properties = validProperties();
        properties.getExternal().setGrpcAuthRequired(false);
        properties.getExternal().setUserserviceInternalGrpcToken("legacy-private-token");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ExternalGrpcAuthValidator(properties).validate());
        assertFalse(error.getMessage().contains("legacy-private-token"));
    }

    @Test
    void rejectsInvalidCallerConfigurationWithoutDisclosingSecret() {
        AppProperties properties = validProperties();
        properties.getExternal().getUserserviceJwt().setScopes("user.auth.session.read,user.admin.write");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ExternalGrpcAuthValidator(properties).validate());
        assertFalse(error.getMessage().contains(SECRET));
    }

    private AppProperties validProperties() {
        AppProperties properties = new AppProperties();
        properties.getExternal().setPayserviceJwt("pay-service-test-jwt");
        properties.getExternal().setAiserviceHmacCaller("aisocialgame");
        properties.getExternal().setAiserviceHmacSecret("ai-service-test-secret");
        properties.getExternal().getUserserviceJwt().setSecret(SECRET);
        return properties;
    }
}
