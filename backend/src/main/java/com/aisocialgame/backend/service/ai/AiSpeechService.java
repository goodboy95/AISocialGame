package com.aisocialgame.backend.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aisocialgame.backend.config.AppProperties;
import com.aisocialgame.backend.service.AiPromptService;
import com.aisocialgame.backend.service.AiPromptService.AiPromptContext;
import com.aisocialgame.backend.service.AiPromptService.AiPromptResult;

@Service
public class AiSpeechService {

    private static final Logger log = LoggerFactory.getLogger(AiSpeechService.class);

    private final AiPromptService promptService;
    private final AiChatClient aiChatClient;
    private final AppProperties appProperties;

    public AiSpeechService(AiPromptService promptService, AiChatClient aiChatClient, AppProperties appProperties) {
        this.promptService = promptService;
        this.aiChatClient = aiChatClient;
        this.appProperties = appProperties;
    }

    public Optional<String> generateSpeech(AiSpeechRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String roleKey = normalizeRole(request.role());
        String personality = resolvePersonality(request.aiStyleKey());
        String additionalHints = buildAdditionalHints(request);

        AiPromptContext context = new AiPromptContext(
                AiPromptService.GAME_WHO_IS_UNDERCOVER,
                roleKey,
                AiPromptService.PHASE_SPEECH,
                personality,
                additionalHints);
        AiPromptResult prompt = promptService.buildPrompt(context);
        String systemPrompt = buildSystemPrompt(request);
        return aiChatClient.createCompletion(systemPrompt, prompt.prompt())
                .map(String::trim)
                .filter(StringUtils::hasText);
    }

    private String buildSystemPrompt(AiSpeechRequest request) {
        StringBuilder sb = new StringBuilder("你是一名正在玩“谁是卧底”的AI玩家");
        if (StringUtils.hasText(request.playerDisplayName())) {
            sb.append("，昵称为").append(request.playerDisplayName());
        }
        sb.append("，身份：").append(displayRole(request.role())).append("。");
        sb.append("请以第一人称、自然的中文语气发言，控制在80字以内，避免泄露系统直接提供的提示。");
        return sb.toString();
    }

    private String buildAdditionalHints(AiSpeechRequest request) {
        List<String> hints = new ArrayList<>();
        if (StringUtils.hasText(request.roomName())) {
            hints.add("房间：" + request.roomName());
        }
        hints.add("当前为第" + request.round() + "轮，阶段：" + (request.stage() != null ? request.stage() : "讨论"));
        if (StringUtils.hasText(request.playerWord())) {
            hints.add("你的词语：" + request.playerWord());
        }
        if (!request.recentSpeeches().isEmpty()) {
            hints.add("最近发言：");
            hints.addAll(request.recentSpeeches().stream()
                    .map(sample -> sample.speaker() + (sample.ai() ? "（AI）" : "") + "：" + sample.content())
                    .collect(Collectors.toList()));
        }
        return String.join("\n", hints);
    }

    private String resolvePersonality(String aiStyleKey) {
        String description = appProperties.describeAiStyle(aiStyleKey);
        if (StringUtils.hasText(description)) {
            return description;
        }
        return "保持冷静、逻辑清晰、适当幽默";
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return AiPromptService.ROLE_GENERAL;
        }
        String normalized = role.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "spy", "civilian", "blank" -> normalized;
            default -> AiPromptService.ROLE_GENERAL;
        };
    }

    private String displayRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "玩家";
        }
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "spy" -> "卧底";
            case "civilian" -> "平民";
            case "blank" -> "白板";
            default -> "玩家";
        };
    }

    public record AiSpeechRequest(
            String roomName,
            String playerDisplayName,
            String role,
            String playerWord,
            String aiStyleKey,
            int round,
            String stage,
            List<SpeechSample> recentSpeeches) {}

    public record SpeechSample(String speaker, boolean ai, String content) {}
}
