package com.aisocialgame.integration.grpc.client;

import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.integration.grpc.dto.LedgerEntrySnapshot;
import com.aisocialgame.integration.grpc.dto.PagedLedgerSnapshot;
import fireflychat.billing.v1.BillingBalanceServiceGrpc;
import fireflychat.billing.v1.BillingQueryServiceGrpc;
import fireflychat.billing.v1.GetProjectBalanceRequest;
import fireflychat.billing.v1.GetPublicBalanceRequest;
import fireflychat.billing.v1.ListLedgerEntriesRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingGrpcClient {

    @GrpcClient("billing")
    private BillingBalanceServiceGrpc.BillingBalanceServiceBlockingStub balanceStub;

    @GrpcClient("billing")
    private BillingQueryServiceGrpc.BillingQueryServiceBlockingStub queryStub;

    public BalanceSnapshot getBalance(String projectKey, long userId) {
        try {
            var publicResponse = balanceStub.getPublicBalance(GetPublicBalanceRequest.newBuilder()
                    .setUserId(userId)
                    .build());
            var projectResponse = balanceStub.getProjectBalance(GetProjectBalanceRequest.newBuilder()
                    .setProjectKey(projectKey)
                    .setUserId(userId)
                    .build());
            var projectBalance = projectResponse.getBalance();
            return new BalanceSnapshot(
                    publicResponse.getPublicPermanentTokens(),
                    projectBalance.getTempTokens(),
                    projectBalance.getPermanentTokens(),
                    GrpcTimeMapper.toInstant(projectBalance.getTempExpiresAt())
            );
        } catch (StatusRuntimeException ex) {
            throw toApiException(ex);
        }
    }

    public PagedLedgerSnapshot listLedgerEntries(long userId, String projectKey, int page, int size) {
        try {
            var response = queryStub.listLedgerEntries(ListLedgerEntriesRequest.newBuilder()
                    .setUserId(userId)
                    .setProjectKey(projectKey == null ? "" : projectKey)
                    .setPage(page)
                    .setSize(size)
                    .build());
            List<LedgerEntrySnapshot> entries = response.getEntriesList().stream()
                    .map(entry -> new LedgerEntrySnapshot(
                            entry.getId(),
                            entry.getRequestId(),
                            entry.getProjectKey(),
                            entry.getType(),
                            entry.getTokenDeltaTemp(),
                            entry.getTokenDeltaPermanent(),
                            entry.getTokenDeltaPublic(),
                            entry.getBalanceTemp(),
                            entry.getBalancePermanent(),
                            entry.getBalancePublic(),
                            entry.getSource(),
                            GrpcTimeMapper.toInstant(entry.getCreatedAt()),
                            entry.getMetadataMap()
                    ))
                    .toList();
            var info = response.getPageInfo();
            return new PagedLedgerSnapshot(info.getPage(), info.getSize(), info.getTotal(), entries);
        } catch (StatusRuntimeException ex) {
            throw toApiException(ex);
        }
    }

    private ApiException toApiException(StatusRuntimeException ex) {
        Status.Code code = ex.getStatus().getCode();
        HttpStatus status = switch (code) {
            case INVALID_ARGUMENT, FAILED_PRECONDITION -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        String message = ex.getStatus().getDescription();
        if (message == null || message.isBlank()) {
            message = "积分服务调用失败";
        }
        return new ApiException(status, message);
    }
}
