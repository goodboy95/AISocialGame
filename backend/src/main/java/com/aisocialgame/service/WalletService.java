package com.aisocialgame.service;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.grpc.client.BillingGrpcClient;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.integration.grpc.dto.CheckinResult;
import com.aisocialgame.integration.grpc.dto.CheckinStatusResult;
import com.aisocialgame.integration.grpc.dto.LedgerEntrySnapshot;
import com.aisocialgame.integration.grpc.dto.PagedResult;
import com.aisocialgame.integration.grpc.dto.RedeemResult;
import com.aisocialgame.integration.grpc.dto.RedemptionRecordSnapshot;
import com.aisocialgame.integration.grpc.dto.UsageRecordSnapshot;
import com.aisocialgame.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class WalletService {
    private final BillingGrpcClient billingGrpcClient;
    private final BalanceService balanceService;
    private final AppProperties appProperties;

    public WalletService(BillingGrpcClient billingGrpcClient,
                         BalanceService balanceService,
                         AppProperties appProperties) {
        this.billingGrpcClient = billingGrpcClient;
        this.balanceService = balanceService;
        this.appProperties = appProperties;
    }

    public CheckinResult checkin(User user) {
        long userId = requireExternalUserId(user);
        String requestId = appProperties.getProjectKey() + ":checkin:" + userId + ":"
                + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        return billingGrpcClient.checkin(requestId, appProperties.getProjectKey(), userId);
    }

    public CheckinStatusResult getCheckinStatus(User user) {
        long userId = requireExternalUserId(user);
        return billingGrpcClient.getCheckinStatus(appProperties.getProjectKey(), userId);
    }

    public BalanceSnapshot getBalance(User user) {
        return balanceService.getUserBalance(requireExternalUserId(user));
    }

    public PagedResult<UsageRecordSnapshot> getUsageRecords(User user, int page, int size) {
        long userId = requireExternalUserId(user);
        return billingGrpcClient.listUsageRecords(appProperties.getProjectKey(), userId, normalizePage(page), normalizeSize(size));
    }

    public PagedResult<LedgerEntrySnapshot> getLedgerEntries(User user, int page, int size) {
        long userId = requireExternalUserId(user);
        return billingGrpcClient.listLedgerEntriesForWallet(userId, appProperties.getProjectKey(), normalizePage(page), normalizeSize(size));
    }

    public RedeemResult redeemCode(User user, String code) {
        long userId = requireExternalUserId(user);
        if (!StringUtils.hasText(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "兑换码不能为空");
        }
        String requestId = appProperties.getProjectKey() + ":redeem:" + userId + ":" + UUID.randomUUID();
        return billingGrpcClient.redeemCode(requestId, appProperties.getProjectKey(), userId, code.trim());
    }

    public PagedResult<RedemptionRecordSnapshot> getRedemptionHistory(User user, int page, int size) {
        long userId = requireExternalUserId(user);
        return billingGrpcClient.getRedemptionHistory(appProperties.getProjectKey(), userId, normalizePage(page), normalizeSize(size));
    }

    private long requireExternalUserId(User user) {
        if (user == null || user.getExternalUserId() == null || user.getExternalUserId() <= 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user.getExternalUserId();
    }

    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
