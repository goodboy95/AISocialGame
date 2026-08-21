package com.aisocialgame;

import com.aisocialgame.config.PayServiceJwtStartupGuard;
import com.aisocialgame.config.AiServiceTransportStartupGuard;
import com.aisocialgame.config.RuntimeConfigurationStartupGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;

@SpringBootApplication
public class AiSocialGameApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AiSocialGameApplication.class);
        application.addInitializers(context -> {
            ConfigurableEnvironment environment = (ConfigurableEnvironment) context.getEnvironment();
            RuntimeConfigurationStartupGuard.validateBeforeServerCreation(environment, System.getenv());
            PayServiceJwtStartupGuard.validateBeforeServerCreation(environment, System.getenv());
            AiServiceTransportStartupGuard.validateBeforeServerCreation(environment, System.getenv());
        });
        application.run(args);
    }
}
