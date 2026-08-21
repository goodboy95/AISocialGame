package com.aisocialgame.config;

import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BillingGrpcChannelConfigurationTest {

    @Test
    void disablesTransportManagedRetriesOnlyForBillingChannel() {
        BillingGrpcChannelConfiguration configuration = new BillingGrpcChannelConfiguration();
        ManagedChannelBuilder<?> billing = mock(ManagedChannelBuilder.class);
        ManagedChannelBuilder<?> other = mock(ManagedChannelBuilder.class);

        configuration.billingTransportRetryPolicy().accept(billing, "billing");
        configuration.billingTransportRetryPolicy().accept(other, "user");

        verify(billing).disableRetry();
        verifyNoInteractions(other);
    }
}
