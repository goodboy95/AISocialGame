package com.aisocialgame.config;

import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BillingGrpcChannelConfiguration {
    static final String BILLING_CHANNEL_NAME = "billing";

    @Bean
    GrpcChannelConfigurer billingTransportRetryPolicy() {
        return (builder, channelName) -> {
            if (BILLING_CHANNEL_NAME.equals(channelName)) {
                builder.disableRetry();
            }
        };
    }
}
