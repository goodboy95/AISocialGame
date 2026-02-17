package com.aisocialgame;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.dto.AiEmbeddingsRequest;
import com.aisocialgame.dto.AiOcrRequest;
import com.aisocialgame.integration.grpc.client.AiGrpcClient;
import com.aisocialgame.integration.grpc.dto.AiEmbeddingsResult;
import com.aisocialgame.integration.grpc.dto.AiOcrResult;
import com.aisocialgame.model.User;
import com.aisocialgame.service.AiProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProxyServiceTest {

    @Mock
    private AiGrpcClient aiGrpcClient;

    private AiProxyService aiProxyService;

    @BeforeEach
    void setUp() throws Exception {
        AppProperties appProperties = new AppProperties();
        appProperties.setProjectKey("aisocialgame");
        appProperties.getAi().setDefaultModel("default-model");
        appProperties.getAi().setSystemUserId(1L);
        aiProxyService = new AiProxyService(aiGrpcClient, appProperties);
    }

    @Test
    void embeddingsShouldMapRequestWithDefaultNormalize() {
        User user = new User();
        user.setExternalUserId(1001L);
        user.setSessionId("session-1001");
        AiEmbeddingsRequest request = new AiEmbeddingsRequest();
        request.setInput(List.of("hello", "world"));
        request.setNormalize(null);

        when(aiGrpcClient.embeddings(anyString(), anyLong(), anyString(), anyString(), eq(List.of("hello", "world")), anyBoolean()))
                .thenReturn(new AiEmbeddingsResult("model-a", 3, List.of(List.of(0.1f, 0.2f, 0.3f)), 2));

        aiProxyService.embeddings(request, user);

        verify(aiGrpcClient).embeddings("aisocialgame", 1001L, "session-1001", "default-model", List.of("hello", "world"), true);
    }

    @Test
    void ocrShouldMapRequestParams() {
        User user = new User();
        user.setExternalUserId(1002L);
        user.setSessionId("session-1002");
        AiOcrRequest request = new AiOcrRequest();
        request.setImageUrl("https://example.com/a.png");
        request.setOutputType("JSON");

        when(aiGrpcClient.ocrParse(anyString(), anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AiOcrResult("req-1", "ocr-a", "JSON", "{}", "{}"));

        aiProxyService.ocrParse(request, user);

        ArgumentCaptor<com.aisocialgame.integration.grpc.dto.AiOcrParams> paramsCaptor =
                ArgumentCaptor.forClass(com.aisocialgame.integration.grpc.dto.AiOcrParams.class);
        verify(aiGrpcClient).ocrParse(eq("aisocialgame"), eq(1002L), eq("session-1002"), eq("default-model"), paramsCaptor.capture());
        assertEquals("https://example.com/a.png", paramsCaptor.getValue().imageUrl());
        assertEquals("JSON", paramsCaptor.getValue().outputType());
    }
}
