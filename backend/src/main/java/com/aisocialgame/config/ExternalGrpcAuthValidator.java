package com.aisocialgame.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("!test")
public class ExternalGrpcAuthValidator {

    private final AppProperties appProperties;

    public ExternalGrpcAuthValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void validate() {
        AppProperties.External external = appProperties.getExternal();
        if (external == null || !external.isGrpcAuthRequired()) {
            return;
        }

        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(external.getUserserviceInternalGrpcToken())) {
            missing.add("APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN");
        }
        if (!StringUtils.hasText(external.getPayserviceJwt())) {
            missing.add("APP_EXTERNAL_PAYSERVICE_JWT");
        }
        if (!StringUtils.hasText(external.getAiserviceHmacCaller())) {
            missing.add("APP_EXTERNAL_AISERVICE_HMAC_CALLER");
        }
        if (!StringUtils.hasText(external.getAiserviceHmacSecret())) {
            missing.add("APP_EXTERNAL_AISERVICE_HMAC_SECRET");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required gRPC auth configuration: " + String.join(", ", missing));
        }
    }
}
