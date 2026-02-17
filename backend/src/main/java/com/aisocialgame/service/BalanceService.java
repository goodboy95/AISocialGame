package com.aisocialgame.service;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.integration.grpc.client.BillingGrpcClient;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.model.User;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {
    private final BillingGrpcClient billingGrpcClient;
    private final AppProperties appProperties;

    public BalanceService(BillingGrpcClient billingGrpcClient, AppProperties appProperties) {
        this.billingGrpcClient = billingGrpcClient;
        this.appProperties = appProperties;
    }

    public BalanceSnapshot getUserBalance(User user) {
        if (user == null || user.getExternalUserId() == null || user.getExternalUserId() <= 0) {
            return BalanceSnapshot.empty();
        }
        return billingGrpcClient.getBalance(appProperties.getProjectKey(), user.getExternalUserId());
    }

    public BalanceSnapshot getUserBalance(long externalUserId) {
        if (externalUserId <= 0) {
            return BalanceSnapshot.empty();
        }
        return billingGrpcClient.getBalance(appProperties.getProjectKey(), externalUserId);
    }
}
