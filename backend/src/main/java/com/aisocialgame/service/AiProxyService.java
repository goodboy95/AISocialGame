package com.aisocialgame.service;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.dto.AiChatRequest;
import com.aisocialgame.dto.AiEmbeddingsRequest;
import com.aisocialgame.dto.AiOcrRequest;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.grpc.client.AiGrpcClient;
import com.aisocialgame.integration.grpc.dto.AiChatMessageDto;
import com.aisocialgame.integration.grpc.dto.AiChatResult;
import com.aisocialgame.integration.grpc.dto.AiEmbeddingsResult;
import com.aisocialgame.integration.grpc.dto.AiModelOptionDto;
import com.aisocialgame.integration.grpc.dto.AiOcrParams;
import com.aisocialgame.integration.grpc.dto.AiOcrResult;
import com.aisocialgame.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiProxyService {
    private final AiGrpcClient aiGrpcClient;
    private final ProjectCreditService projectCreditService;
    private final AppProperties appProperties;

    public AiProxyService(AiGrpcClient aiGrpcClient,
                          ProjectCreditService projectCreditService,
                          AppProperties appProperties) {
        this.aiGrpcClient = aiGrpcClient;
        this.projectCreditService = projectCreditService;
        this.appProperties = appProperties;
    }

    public List<AiModelOptionDto> listModels() {
        return aiGrpcClient.listModels();
    }

    public AiChatResult chat(AiChatRequest request, User user) {
        long userId = user != null && user.getExternalUserId() != null && user.getExternalUserId() > 0
                ? user.getExternalUserId()
                : appProperties.getAi().getSystemUserId();
        String sessionId = user != null && StringUtils.hasText(user.getSessionId()) ? user.getSessionId() : "";
        return chatByIdentity(request, userId, sessionId);
    }

    public AiChatResult chatByIdentity(AiChatRequest request, long userId, String sessionId) {
        List<String> candidateModels = resolveChatModels(request.getModel());
        boolean explicitModel = StringUtils.hasText(request.getModel());
        List<AiChatMessageDto> messages = request.getMessages().stream()
                .map(message -> new AiChatMessageDto(message.getRole(), message.getContent()))
                .toList();
        ApiException lastError = null;
        AiChatResult result = null;
        for (int i = 0; i < candidateModels.size(); i++) {
            String model = candidateModels.get(i);
            try {
                result = aiGrpcClient.chatCompletions(
                        appProperties.getProjectKey(),
                        userId,
                        sessionId == null ? "" : sessionId,
                        model,
                        messages
                );
                break;
            } catch (ApiException ex) {
                lastError = ex;
                // For explicit model selection, fail fast and expose the backend error.
                if (explicitModel) {
                    throw ex;
                }
                // Retry with next fallback model only for model-level bad request failures.
                boolean canRetry = ex.getStatus() == HttpStatus.BAD_REQUEST && i < candidateModels.size() - 1;
                if (!canRetry) {
                    throw ex;
                }
            }
        }
        if (result == null) {
            if (lastError != null) {
                throw lastError;
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI 服务调用失败");
        }
        Map<String, String> metadata = new HashMap<>();
        metadata.put("modelKey", result.modelKey());
        metadata.put("promptTokens", String.valueOf(result.promptTokens()));
        metadata.put("completionTokens", String.valueOf(result.completionTokens()));
        applyConsume(userId, Math.max(0, result.promptTokens()) + Math.max(0, result.completionTokens()), "AI_CHAT", metadata);
        return result;
    }

    public AiEmbeddingsResult embeddings(AiEmbeddingsRequest request, User user) {
        long userId = user != null && user.getExternalUserId() != null && user.getExternalUserId() > 0
                ? user.getExternalUserId()
                : appProperties.getAi().getSystemUserId();
        String sessionId = user != null && StringUtils.hasText(user.getSessionId()) ? user.getSessionId() : "";
        String model = resolveModel(request.getModel());
        boolean normalize = request.getNormalize() == null || request.getNormalize();
        AiEmbeddingsResult result = aiGrpcClient.embeddings(
                appProperties.getProjectKey(),
                userId,
                sessionId,
                model,
                request.getInput(),
                normalize
        );
        Map<String, String> metadata = new HashMap<>();
        metadata.put("modelKey", result.modelKey());
        metadata.put("promptTokens", String.valueOf(result.promptTokens()));
        applyConsume(userId, Math.max(0, result.promptTokens()), "AI_EMBEDDINGS", metadata);
        return result;
    }

    public AiOcrResult ocrParse(AiOcrRequest request, User user) {
        long userId = user != null && user.getExternalUserId() != null && user.getExternalUserId() > 0
                ? user.getExternalUserId()
                : appProperties.getAi().getSystemUserId();
        String sessionId = user != null && StringUtils.hasText(user.getSessionId()) ? user.getSessionId() : "";
        String model = resolveModel(request.getModel());
        return aiGrpcClient.ocrParse(
                appProperties.getProjectKey(),
                userId,
                sessionId,
                model,
                new AiOcrParams(
                        request.getImageUrl(),
                        request.getImageBase64(),
                        request.getDocumentUrl(),
                        request.getPages(),
                        request.getOutputType()
                )
        );
    }

    private void applyConsume(long userId, long billedTokens, String source, Map<String, String> metadata) {
        if (userId <= 0 || billedTokens <= 0) {
            return;
        }
        projectCreditService.consumeProjectTokens(userId, billedTokens, source, metadata, null);
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel.trim();
        }
        if (StringUtils.hasText(appProperties.getAi().getDefaultModel())) {
            return appProperties.getAi().getDefaultModel().trim();
        }
        try {
            return aiGrpcClient.listModels().stream()
                    .filter(model -> StringUtils.hasText(model.type()) && model.type().toUpperCase().contains("TEXT"))
                    .map(item -> StringUtils.hasText(item.displayName()) ? item.displayName().trim() : "")
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> resolveChatModels(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return List.of(requestedModel.trim());
        }
        Set<String> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(appProperties.getAi().getDefaultModel())) {
            candidates.add(appProperties.getAi().getDefaultModel().trim());
        }
        try {
            for (AiModelOptionDto model : aiGrpcClient.listModels()) {
                String type = model.type() == null ? "" : model.type().toUpperCase();
                if (!type.contains("TEXT")) {
                    continue;
                }
                if (StringUtils.hasText(String.valueOf(model.id()))) {
                    candidates.add(String.valueOf(model.id()));
                }
                if (StringUtils.hasText(model.displayName())) {
                    candidates.add(model.displayName().trim());
                }
            }
        } catch (Exception ignored) {
            // Keep best-effort fallback candidates.
        }
        if (candidates.isEmpty()) {
            String fallback = resolveModel(requestedModel);
            if (StringUtils.hasText(fallback)) {
                candidates.add(fallback);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add("");
        }
        return new ArrayList<>(candidates);
    }
}
