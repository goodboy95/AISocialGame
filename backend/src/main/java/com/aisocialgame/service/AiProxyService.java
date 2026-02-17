package com.aisocialgame.service;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.dto.AiChatRequest;
import com.aisocialgame.dto.AiEmbeddingsRequest;
import com.aisocialgame.dto.AiOcrRequest;
import com.aisocialgame.integration.grpc.client.AiGrpcClient;
import com.aisocialgame.integration.grpc.dto.AiChatMessageDto;
import com.aisocialgame.integration.grpc.dto.AiChatResult;
import com.aisocialgame.integration.grpc.dto.AiEmbeddingsResult;
import com.aisocialgame.integration.grpc.dto.AiModelOptionDto;
import com.aisocialgame.integration.grpc.dto.AiOcrParams;
import com.aisocialgame.integration.grpc.dto.AiOcrResult;
import com.aisocialgame.model.User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiProxyService {
    private final AiGrpcClient aiGrpcClient;
    private final AppProperties appProperties;

    public AiProxyService(AiGrpcClient aiGrpcClient, AppProperties appProperties) {
        this.aiGrpcClient = aiGrpcClient;
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
        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : appProperties.getAi().getDefaultModel();
        List<AiChatMessageDto> messages = request.getMessages().stream()
                .map(message -> new AiChatMessageDto(message.getRole(), message.getContent()))
                .toList();
        return aiGrpcClient.chatCompletions(
                appProperties.getProjectKey(),
                userId,
                sessionId == null ? "" : sessionId,
                model,
                messages
        );
    }

    public AiEmbeddingsResult embeddings(AiEmbeddingsRequest request, User user) {
        long userId = user != null && user.getExternalUserId() != null && user.getExternalUserId() > 0
                ? user.getExternalUserId()
                : appProperties.getAi().getSystemUserId();
        String sessionId = user != null && StringUtils.hasText(user.getSessionId()) ? user.getSessionId() : "";
        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : appProperties.getAi().getDefaultModel();
        boolean normalize = request.getNormalize() == null || request.getNormalize();
        return aiGrpcClient.embeddings(
                appProperties.getProjectKey(),
                userId,
                sessionId,
                model,
                request.getInput(),
                normalize
        );
    }

    public AiOcrResult ocrParse(AiOcrRequest request, User user) {
        long userId = user != null && user.getExternalUserId() != null && user.getExternalUserId() > 0
                ? user.getExternalUserId()
                : appProperties.getAi().getSystemUserId();
        String sessionId = user != null && StringUtils.hasText(user.getSessionId()) ? user.getSessionId() : "";
        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : appProperties.getAi().getDefaultModel();
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
}
