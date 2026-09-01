package com.aisocialgame.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Test-classpath-only authorization marker; this class must never enter the production jar. */
@Configuration
public class RuntimeProfileTestAuthorizationConfiguration {
    @Bean
    RuntimeProfileTestAuthorization runtimeProfileTestAuthorization() {
        return new RuntimeProfileTestAuthorization() {
        };
    }
}
