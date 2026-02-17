package com.aisocialgame.service;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.config.PromptProperties;
import com.aisocialgame.integration.grpc.client.AiGrpcClient;
import com.aisocialgame.integration.grpc.dto.AiChatMessageDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiGameSpeechService {
    private final AiGrpcClient aiGrpcClient;
    private final AppProperties appProperties;
    private final PromptProperties promptProperties;

    public AiGameSpeechService(AiGrpcClient aiGrpcClient,
                               AppProperties appProperties,
                               PromptProperties promptProperties) {
        this.aiGrpcClient = aiGrpcClient;
        this.appProperties = appProperties;
        this.promptProperties = promptProperties;
    }

    public String buildDescription(String word) {
        String fallback = formatTemplate(promptProperties.getAiTalk().getDescriptionTemplate(), "%s，我觉得这个词很特别。", word);
        String prompt = "你在玩谁是卧底游戏，请根据词语给一句不直接暴露词语的描述。词语：" + word + "。输出不超过20字。";
        return tryChat(prompt, fallback);
    }

    public String buildSuspicion(int seatNumber) {
        String fallback = formatTemplate(promptProperties.getAiTalk().getSuspicionTemplate(), "我觉得%s号有问题", seatNumber);
        String prompt = "你在玩狼人杀讨论阶段，请给出一句怀疑发言，不超过20字，目标座位：" + seatNumber + "号。";
        return tryChat(prompt, fallback);
    }

    private String tryChat(String prompt, String fallback) {
        try {
            var response = aiGrpcClient.chatCompletions(
                    appProperties.getProjectKey(),
                    appProperties.getAi().getSystemUserId(),
                    "",
                    appProperties.getAi().getDefaultModel(),
                    List.of(new AiChatMessageDto("user", prompt))
            );
            String content = sanitize(response.content());
            if (StringUtils.hasText(content)) {
                return content;
            }
        } catch (Exception ignored) {
            // AI 服务异常时回退本地模板，确保游戏可继续。
        }
        return fallback;
    }

    private String sanitize(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String normalized = content.strip();
        int newline = normalized.indexOf('\n');
        if (newline > 0) {
            normalized = normalized.substring(0, newline).trim();
        }
        if (normalized.length() > 30) {
            normalized = normalized.substring(0, 30);
        }
        return normalized;
    }

    private String formatTemplate(String template, String fallback, Object... args) {
        String effective = StringUtils.hasText(template) ? template : fallback;
        try {
            return String.format(effective, args);
        } catch (Exception ignored) {
            return String.format(fallback, args);
        }
    }
}
