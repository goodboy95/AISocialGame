package com.aisocialgame.service;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.dto.AiChatRequest;
import com.aisocialgame.dto.AiChatResponse;
import com.aisocialgame.dto.AiMessageRequest;
import com.aisocialgame.dto.AiModelView;
import com.aisocialgame.dto.admin.AdminDashboardSummaryResponse;
import com.aisocialgame.dto.admin.AdminIntegrationStatusResponse;
import com.aisocialgame.dto.admin.AdminLedgerPageResponse;
import com.aisocialgame.dto.admin.AdminUserView;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.grpc.client.BillingGrpcClient;
import com.aisocialgame.integration.grpc.client.UserGrpcClient;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.integration.grpc.dto.ExternalUserProfile;
import com.aisocialgame.repository.CommunityPostRepository;
import com.aisocialgame.repository.GameStateRepository;
import com.aisocialgame.repository.RoomRepository;
import com.aisocialgame.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AdminOpsService {
    private final UserGrpcClient userGrpcClient;
    private final BillingGrpcClient billingGrpcClient;
    private final AiProxyService aiProxyService;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final CommunityPostRepository communityPostRepository;
    private final GameStateRepository gameStateRepository;
    private final AppProperties appProperties;

    public AdminOpsService(UserGrpcClient userGrpcClient,
                           BillingGrpcClient billingGrpcClient,
                           AiProxyService aiProxyService,
                           UserRepository userRepository,
                           RoomRepository roomRepository,
                           CommunityPostRepository communityPostRepository,
                           GameStateRepository gameStateRepository,
                           AppProperties appProperties) {
        this.userGrpcClient = userGrpcClient;
        this.billingGrpcClient = billingGrpcClient;
        this.aiProxyService = aiProxyService;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.communityPostRepository = communityPostRepository;
        this.gameStateRepository = gameStateRepository;
        this.appProperties = appProperties;
    }

    public AdminDashboardSummaryResponse dashboardSummary() {
        long users = userRepository.count();
        long rooms = roomRepository.count();
        long posts = communityPostRepository.count();
        long gameStates = gameStateRepository.count();
        int modelCount;
        try {
            modelCount = aiProxyService.listModels().size();
        } catch (Exception e) {
            modelCount = 0;
        }
        return new AdminDashboardSummaryResponse(users, rooms, posts, gameStates, modelCount);
    }

    public AdminIntegrationStatusResponse integrationStatus() {
        long probeUserId = Math.max(1, appProperties.getAi().getSystemUserId());
        List<AdminIntegrationStatusResponse.ServiceStatus> statuses = List.of(
                probe("user-service", () -> userGrpcClient.getUserBasic(probeUserId)),
                probe("pay-service", () -> billingGrpcClient.getBalance(appProperties.getProjectKey(), probeUserId)),
                probe("ai-service", aiProxyService::listModels)
        );
        return new AdminIntegrationStatusResponse(statuses);
    }

    public AdminUserView getUser(long userId) {
        ExternalUserProfile profile = userGrpcClient.getUserBasic(userId);
        if (profile == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        var banStatus = userGrpcClient.getBanStatus(userId);
        BalanceSnapshot balance = billingGrpcClient.getBalance(appProperties.getProjectKey(), userId);
        return new AdminUserView(profile, banStatus, balance);
    }

    public AdminUserView banUser(long userId, String reason, boolean permanent, Instant expiresAt) {
        userGrpcClient.banUser(userId, reason, permanent, expiresAt, 0);
        return getUser(userId);
    }

    public AdminUserView unbanUser(long userId, String reason) {
        userGrpcClient.unbanUser(userId, reason, 0);
        return getUser(userId);
    }

    public BalanceSnapshot getBalance(long userId) {
        return billingGrpcClient.getBalance(appProperties.getProjectKey(), userId);
    }

    public AdminLedgerPageResponse getLedger(long userId, int page, int size) {
        return new AdminLedgerPageResponse(
                billingGrpcClient.listLedgerEntries(userId, appProperties.getProjectKey(), page, size)
        );
    }

    public List<AiModelView> listModels() {
        return aiProxyService.listModels().stream().map(AiModelView::new).toList();
    }

    public AiChatResponse testChat(long userId, String sessionId, String model, List<AiMessageRequest> messages) {
        AiChatRequest request = new AiChatRequest();
        request.setModel(model);
        request.setMessages(messages);
        long effectiveUserId = userId > 0 ? userId : Math.max(1, appProperties.getAi().getSystemUserId());
        return new AiChatResponse(aiProxyService.chatByIdentity(request, effectiveUserId, sessionId));
    }

    private AdminIntegrationStatusResponse.ServiceStatus probe(String service, Probe probe) {
        try {
            probe.execute();
            return new AdminIntegrationStatusResponse.ServiceStatus(service, true, "ok");
        } catch (ApiException ex) {
            boolean reachable = ex.getStatus() != HttpStatus.SERVICE_UNAVAILABLE && ex.getStatus() != HttpStatus.BAD_GATEWAY;
            return new AdminIntegrationStatusResponse.ServiceStatus(service, reachable, ex.getMessage());
        } catch (Exception ex) {
            return new AdminIntegrationStatusResponse.ServiceStatus(service, false, ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface Probe {
        Object execute();
    }
}
