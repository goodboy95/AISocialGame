package com.aisocialgame.service;

import com.aisocialgame.integration.grpc.client.BillingGrpcClient;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.model.User;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {
    private final BillingGrpcClient billingGrpcClient;
    private final ProjectCreditService projectCreditService;

    public BalanceService(BillingGrpcClient billingGrpcClient, ProjectCreditService projectCreditService) {
        this.billingGrpcClient = billingGrpcClient;
        this.projectCreditService = projectCreditService;
    }

    public BalanceSnapshot getUserBalance(User user) {
        if (user == null || user.getExternalUserId() == null || user.getExternalUserId() <= 0) {
            return BalanceSnapshot.empty();
        }
        return getUserBalance(user.getExternalUserId());
    }

    public BalanceSnapshot getUserBalance(long externalUserId) {
        if (externalUserId <= 0) {
            return BalanceSnapshot.empty();
        }
        long publicTokens;
        try {
            publicTokens = billingGrpcClient.getPublicPermanentTokens(externalUserId);
        } catch (Exception ignored) {
            publicTokens = 0;
        }
        return projectCreditService.getBalance(externalUserId, publicTokens);
    }
}
