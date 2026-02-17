package com.aisocialgame.integration.grpc.client;

import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.grpc.dto.AiChatMessageDto;
import com.aisocialgame.integration.grpc.dto.AiChatResult;
import com.aisocialgame.integration.grpc.dto.AiModelOptionDto;
import fireflychat.ai.v1.AiGatewayServiceGrpc;
import fireflychat.ai.v1.ChatCompletionsRequest;
import fireflychat.ai.v1.ChatMessage;
import fireflychat.ai.v1.ListModelsRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AiGrpcClient {

    @GrpcClient("ai")
    private AiGatewayServiceGrpc.AiGatewayServiceBlockingStub aiStub;

    public List<AiModelOptionDto> listModels() {
        try {
            var response = aiStub.listModels(ListModelsRequest.newBuilder().build());
            return response.getModelsList().stream()
                    .map(model -> new AiModelOptionDto(
                            model.getId(),
                            model.getDisplayName(),
                            model.getProvider(),
                            model.getInputRate(),
                            model.getOutputRate(),
                            model.getType().name()
                    ))
                    .toList();
        } catch (StatusRuntimeException ex) {
            throw toApiException(ex);
        }
    }

    public AiChatResult chatCompletions(String projectKey,
                                        long userId,
                                        String sessionId,
                                        String model,
                                        List<AiChatMessageDto> messages) {
        try {
            ChatCompletionsRequest.Builder builder = ChatCompletionsRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setProjectKey(projectKey == null ? "" : projectKey)
                    .setUserId(userId)
                    .setSessionId(sessionId == null ? "" : sessionId)
                    .setModel(model == null ? "" : model);
            if (messages != null) {
                for (AiChatMessageDto message : messages) {
                    if (message == null) {
                        continue;
                    }
                    builder.addMessages(ChatMessage.newBuilder()
                            .setRole(message.role() == null ? "" : message.role())
                            .setContent(message.content() == null ? "" : message.content())
                            .build());
                }
            }
            var response = aiStub.chatCompletions(builder.build());
            return new AiChatResult(response.getContent(), response.getModelKey(), response.getPromptTokens(), response.getCompletionTokens());
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
            message = "AI 服务调用失败";
        }
        return new ApiException(status, message);
    }
}
