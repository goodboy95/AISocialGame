package com.aisocialgame;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.grpc.client.BillingGrpcClient;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.integration.grpc.dto.CheckinResult;
import com.aisocialgame.integration.grpc.dto.PagedResult;
import com.aisocialgame.model.User;
import com.aisocialgame.service.BalanceService;
import com.aisocialgame.service.WalletService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private BillingGrpcClient billingGrpcClient;
    @Mock
    private BalanceService balanceService;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setProjectKey("aisocialgame");
        walletService = new WalletService(billingGrpcClient, balanceService, appProperties);
    }

    @Test
    void checkinShouldGenerateStableRequestIdAndCallGrpc() {
        User user = mockUser(1001L);
        when(billingGrpcClient.checkin(anyString(), anyString(), anyLong()))
                .thenReturn(new CheckinResult(true, 100, false, "", BalanceSnapshot.empty()));

        walletService.checkin(user);

        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(billingGrpcClient).checkin(requestIdCaptor.capture(), anyString(), anyLong());
        Assertions.assertTrue(requestIdCaptor.getValue().startsWith("aisocialgame:checkin:1001:"));
    }

    @Test
    void redeemCodeShouldRejectBlankCode() {
        User user = mockUser(1001L);
        Assertions.assertThrows(ApiException.class, () -> walletService.redeemCode(user, " "));
    }

    @Test
    void usageRecordsShouldNormalizePageAndSize() {
        User user = mockUser(1002L);
        when(billingGrpcClient.listUsageRecords(anyString(), anyLong(), anyInt(), anyInt()))
                .thenReturn(new PagedResult<>(1, 20, 0, List.of()));

        walletService.getUsageRecords(user, 0, 999);
        verify(billingGrpcClient).listUsageRecords("aisocialgame", 1002L, 1, 100);
    }

    private User mockUser(long externalUserId) {
        User user = new User();
        user.setExternalUserId(externalUserId);
        user.setSessionId("session-" + externalUserId);
        user.setNickname("tester");
        return user;
    }
}
