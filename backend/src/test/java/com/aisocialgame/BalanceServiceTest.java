package com.aisocialgame;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.integration.grpc.client.BillingGrpcClient;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.model.User;
import com.aisocialgame.service.BalanceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BalanceServiceTest {

    @Test
    void shouldReturnEmptyWhenUserHasNoExternalId() {
        BillingGrpcClient billingGrpcClient = Mockito.mock(BillingGrpcClient.class);
        AppProperties properties = new AppProperties();
        properties.setProjectKey("test-project");
        BalanceService balanceService = new BalanceService(billingGrpcClient, properties);

        User user = new User();
        user.setId("u1");
        BalanceSnapshot snapshot = balanceService.getUserBalance(user);
        Assertions.assertEquals(0, snapshot.totalTokens());
        Mockito.verifyNoInteractions(billingGrpcClient);
    }

    @Test
    void shouldQueryBillingServiceWhenExternalUserPresent() {
        BillingGrpcClient billingGrpcClient = Mockito.mock(BillingGrpcClient.class);
        AppProperties properties = new AppProperties();
        properties.setProjectKey("test-project");
        BalanceService balanceService = new BalanceService(billingGrpcClient, properties);
        Mockito.when(billingGrpcClient.getBalance("test-project", 1001L))
                .thenReturn(new BalanceSnapshot(10, 20, 30, null));

        User user = new User();
        user.setExternalUserId(1001L);
        BalanceSnapshot snapshot = balanceService.getUserBalance(user);

        Assertions.assertEquals(60, snapshot.totalTokens());
        Mockito.verify(billingGrpcClient).getBalance("test-project", 1001L);
    }
}
