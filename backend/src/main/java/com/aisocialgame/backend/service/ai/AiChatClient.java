package com.aisocialgame.backend.service.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.aisocialgame.backend.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(AiChatClient.class);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiChatClient(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Optional<String> createCompletion(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(userPrompt)) {
            return Optional.empty();
        }
        AppProperties.AiModelProperties modelConfig = properties.getAiModel();
        if (modelConfig == null || !modelConfig.isConfigured()) {
            log.debug("AI model configuration missing, skip remote completion");
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = buildPayload(modelConfig.getModelName(), systemPrompt, userPrompt);
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(modelConfig.getBaseUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json");
            if (StringUtils.hasText(modelConfig.getToken())) {
                builder.header("Authorization", "Bearer " + modelConfig.getToken());
            }
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseResponse(response.body());
            }
            log.warn("AI model call failed with status {}: {}", response.statusCode(), abbreviate(response.body()));
        } catch (IOException e) {
            log.warn("Failed to call AI model endpoint: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI model request interrupted");
        } catch (Exception e) {
            log.warn("Unexpected error when calling AI model: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Map<String, Object> buildPayload(String modelName, String systemPrompt, String userPrompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("temperature", 0.85);
        payload.put("max_tokens", 256);

        String system = StringUtils.hasText(systemPrompt)
                ? systemPrompt
                : "你是一名参与谁是卧底游戏的AI玩家，请使用简洁中文发言。";
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", userPrompt)
        );
        payload.put("messages", messages);
        return payload;
    }

    private Optional<String> parseResponse(String body) throws IOException {
        if (!StringUtils.hasText(body)) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode first = choices.get(0);
            JsonNode message = first.path("message");
            if (message.hasNonNull("content")) {
                return Optional.of(message.get("content").asText());
            }
            if (first.hasNonNull("text")) {
                return Optional.of(first.get("text").asText());
            }
        }
        JsonNode data = root.path("data");
        if (data.isArray() && data.size() > 0) {
            JsonNode content = data.get(0).path("content");
            if (content.isArray() && content.size() > 0) {
                JsonNode textNode = content.get(0).path("text");
                if (textNode.hasNonNull("value")) {
                    return Optional.of(textNode.get("value").asText());
                }
            }
        }
        return Optional.empty();
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 256 ? value.substring(0, 256) + "..." : value;
    }
}
