package com.aisocialgame.service;

import com.aisocialgame.config.PromptProperties;
import com.aisocialgame.model.Persona;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 负责为房间中的 AI 生成随机昵称。
 * 优先尝试调用可配置的外部 AI 接口（返回 {"name": "..."}），失败则退化为本地随机词库组合。
 */
@Service
public class AiNameService {

    private final Random random = new Random();
    private final RestTemplate restTemplate = new RestTemplate();
    private final PromptProperties promptProperties;

    @Value("${ai.name.endpoint:}")
    private String aiNameEndpoint;

    public AiNameService(PromptProperties promptProperties) {
        this.promptProperties = promptProperties;
    }

    public String generateName(Persona persona) {
        String aiName = tryRemoteGeneration(persona);
        if (StringUtils.hasText(aiName)) {
            return aiName;
        }
        return fallbackName(persona);
    }

    private String tryRemoteGeneration(Persona persona) {
        if (!StringUtils.hasText(aiNameEndpoint)) {
            return null;
        }
        try {
            var payload = Map.of(
                    "persona", persona.getName(),
                    "style", persona.getTrait()
            );
            var response = restTemplate.postForObject(aiNameEndpoint, payload, AiNameResponse.class);
            if (response != null && StringUtils.hasText(response.getName())) {
                return response.getName().trim();
            }
        } catch (Exception ignored) {
            // 网络不可达或接口错误时回退到本地随机生成，避免影响游戏流程
        }
        return null;
    }

    private String fallbackName(Persona persona) {
        List<String> adjectives = promptProperties.getAiName().getAdjectives();
        List<String> suffixes = promptProperties.getAiName().getSuffixes();
        if (adjectives.isEmpty() || suffixes.isEmpty()) {
            return persona.getName();
        }
        String prefix = adjectives.get(random.nextInt(adjectives.size()));
        String suffix = suffixes.get(random.nextInt(suffixes.size()));
        return prefix + persona.getName() + suffix;
    }

    /**
     * 与外部 AI 接口的最小响应协议。
     */
    private static class AiNameResponse {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
