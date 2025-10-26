package com.aisocialgame.backend.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aisocialgame.backend.entity.AiPromptTemplate;
import com.aisocialgame.backend.repository.AiPromptTemplateRepository;

@Service
@Transactional(readOnly = true)
public class AiPromptService {

    public static final String GAME_WHO_IS_UNDERCOVER = "who_is_undercover";
    public static final String GAME_WEREWOLF = "werewolf";

    public static final String ROLE_GENERAL = "general";

    public static final String PHASE_SPEECH = "speech";
    public static final String PHASE_VOTE = "vote";
    public static final String PHASE_NIGHT_ACTION = "night_action";
    public static final String PHASE_DAY_DISCUSSION = "day_discussion";

    private static final Map<String, Map<String, Map<String, String>>> BUILTIN_PROMPTS = createBuiltinPrompts();

    private final AiPromptTemplateRepository promptRepository;

    public AiPromptService(AiPromptTemplateRepository promptRepository) {
        this.promptRepository = promptRepository;
    }

    public AiPromptResult buildPrompt(AiPromptContext context) {
        String gameType = normalize(context.gameType());
        String phaseKey = normalize(context.phaseKey());
        String roleKey = normalizeRole(context.roleKey());
        String personality = context.personality() == null ? "" : context.personality().trim();

        AiPromptTemplate stored = resolveStoredTemplate(gameType, roleKey, phaseKey).orElse(null);
        boolean fallback = stored == null;
        String templateBody = fallback
                ? resolveFallbackTemplate(gameType, roleKey, phaseKey)
                : stored.getContentTemplate();

        String compiled = compileTemplate(templateBody, gameType, roleKey, phaseKey, personality, context.additionalHints());
        return new AiPromptResult(
                compiled,
                fallback ? null : stored.getId(),
                fallback,
                templateBody);
    }

    private Optional<AiPromptTemplate> resolveStoredTemplate(String gameType, String roleKey, String phaseKey) {
        if (!StringUtils.hasText(gameType) || !StringUtils.hasText(phaseKey)) {
            return Optional.empty();
        }

        Optional<AiPromptTemplate> roleSpecific = promptRepository
                .findByGameTypeAndRoleKeyAndPhaseKey(gameType, roleKey, phaseKey);

        if (roleSpecific.isPresent()) {
            return roleSpecific;
        }

        if (!Objects.equals(roleKey, ROLE_GENERAL)) {
            return promptRepository.findByGameTypeAndRoleKeyAndPhaseKey(gameType, ROLE_GENERAL, phaseKey);
        }

        return Optional.empty();
    }

    private String resolveFallbackTemplate(String gameType, String roleKey, String phaseKey) {
        Map<String, Map<String, String>> gameFallback = BUILTIN_PROMPTS.getOrDefault(gameType, Collections.emptyMap());
        Map<String, String> roleFallback = gameFallback.getOrDefault(roleKey, gameFallback.getOrDefault(ROLE_GENERAL, Collections.emptyMap()));
        return roleFallback.getOrDefault(phaseKey,
                "系统提示：\n- 游戏：" + gameType + "\n- 阶段：" + phaseKey + "\n- AI 性格：{{personality}}\n请根据当前阶段给出自然、结构化的中文发言。");
    }

    private String compileTemplate(
            String template,
            String gameType,
            String roleKey,
            String phaseKey,
            String personality,
            String additional) {
        String rendered = template;
        rendered = rendered.replace("{{game_type}}", gameType);
        rendered = rendered.replace("{{phase_key}}", phaseKey);
        rendered = rendered.replace("{{role_key}}", roleKey);
        rendered = rendered.replace("{{personality}}", personality);
        rendered = rendered.replace("{{additional_hints}}", additional == null ? "" : additional.trim());
        return rendered;
    }

    private static Map<String, Map<String, Map<String, String>>> createBuiltinPrompts() {
        Map<String, Map<String, Map<String, String>>> map = new HashMap<>();

        Map<String, Map<String, String>> undercover = new HashMap<>();
        Map<String, String> undercoverGeneral = new HashMap<>();
        undercoverGeneral.put(PHASE_SPEECH, """
                系统提示：
                - 游戏：谁是卧底
                - 阶段：发言阶段
                - 你的性格与倾向：{{personality}}
                请以自然、可信的语气进行发言。建议按照“立场表态 → 线索分析 → 结论”三段式结构组织语言，合理隐藏身份并回应前序玩家观点。若有额外线索可参考：{{additional_hints}}。
                """);
        undercoverGeneral.put(PHASE_VOTE, """
                系统提示：
                - 游戏：谁是卧底
                - 阶段：投票阶段
                - 你的性格与倾向：{{personality}}
                在说明投票对象前，先用简短语句概括你的判断依据，再给出最终投票目标和一句收尾提醒。保持语气克制，同时兼顾策略性与可信度。
                """);
        undercover.put(ROLE_GENERAL, undercoverGeneral);
        map.put(GAME_WHO_IS_UNDERCOVER, undercover);

        Map<String, Map<String, String>> werewolf = new HashMap<>();

        Map<String, String> werewolfVillager = new HashMap<>();
        werewolfVillager.put(PHASE_DAY_DISCUSSION, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：白天发言
                - 角色：村民
                - 个性：{{personality}}
                结合过往发言列出至少两条推理线索，强调你的普通身份与理性分析，并主动表达下一步希望调查或关注的对象。
                """);
        werewolfVillager.put(PHASE_VOTE, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：投票
                - 角色：村民
                - 个性：{{personality}}
                先重述你当前的怀疑目标与理由，再明确投票对象，并以一句集合团队的号召语收尾。
                """);
        werewolf.put("villager", werewolfVillager);

        Map<String, String> werewolfSeer = new HashMap<>();
        werewolfSeer.put(PHASE_NIGHT_ACTION, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：夜晚行动
                - 角色：预言家
                - 个性：{{personality}}
                请根据白天信息选择一名玩家查验，并给出核心判断理由和你想验证的假设。输出格式为“目标 → 主要理由 → 若查验结果如何将如何行动”。
                """);
        werewolfSeer.put(PHASE_DAY_DISCUSSION, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：白天发言
                - 角色：预言家
                - 个性：{{personality}}
                在分享昨夜查验结果前，先设定整体发言框架，然后公布结果并提出下一步指引。语气应兼顾权威与可信度。
                """);
        werewolf.put("seer", werewolfSeer);

        Map<String, String> werewolfWitch = new HashMap<>();
        werewolfWitch.put(PHASE_NIGHT_ACTION, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：夜晚行动
                - 角色：女巫
                - 个性：{{personality}}
                根据死亡信息判断是否使用解药，并考虑是否使用毒药。请给出“当前局势评估 → 解药使用决策 → 毒药使用决策”的结构化分析。
                """);
        werewolfWitch.put(PHASE_DAY_DISCUSSION, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：白天发言
                - 角色：女巫
                - 个性：{{personality}}
                说明你夜间行动的考虑，保持信息有限泄露。条理化描述你的疑虑列表，并提出团队行动建议。
                """);
        werewolf.put("witch", werewolfWitch);

        Map<String, String> werewolfHunter = new HashMap<>();
        werewolfHunter.put(PHASE_DAY_DISCUSSION, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：白天发言
                - 角色：猎人
                - 个性：{{personality}}
                结合你的开枪威慑力提出讨论重点。给出两个关注目标，并说明若你被处决将优先锁定的开枪对象。
                """);
        werewolfHunter.put(PHASE_VOTE, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：投票
                - 角色：猎人
                - 个性：{{personality}}
                先明确你的投票决定，再发布开枪宣言，强调他人应如何配合你的判断。
                """);
        werewolf.put("hunter", werewolfHunter);

        Map<String, String> werewolfWolf = new HashMap<>();
        werewolfWolf.put(PHASE_NIGHT_ACTION, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：夜晚行动
                - 角色：狼人
                - 个性：{{personality}}
                从团队视角选择一个猎杀目标。给出“团队目标 → 风险评估 → 行动决策”的三段式说明，保持语言隐蔽且策略性强。
                """);
        werewolfWolf.put(PHASE_DAY_DISCUSSION, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：白天发言
                - 角色：狼人
                - 个性：{{personality}}
                模拟一名无辜身份的发言方式，先复盘昨夜事件，再提出带节奏的讨论方向，暗中保护队友。
                """);
        werewolfWolf.put(PHASE_VOTE, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：投票
                - 角色：狼人
                - 个性：{{personality}}
                隐藏真实意图的前提下，给出投票理由，悄悄引导视线到无辜目标或防止队友暴露。
                """);
        werewolf.put("werewolf", werewolfWolf);

        Map<String, String> werewolfGeneral = new HashMap<>();
        werewolfGeneral.put(PHASE_VOTE, """
                系统提示：
                - 游戏：狼人杀
                - 阶段：投票
                - 角色：{{role_key}}
                - 个性：{{personality}}
                简要回顾讨论要点后，明确你的投票对象，并陈述希望团队配合的行动方向。
                """);
        werewolf.put(ROLE_GENERAL, werewolfGeneral);

        map.put(GAME_WEREWOLF, werewolf);

        return map;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeRole(String value) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized : ROLE_GENERAL;
    }

    public record AiPromptContext(
            String gameType,
            String roleKey,
            String phaseKey,
            String personality,
            String additionalHints) {}

    public record AiPromptResult(
            String prompt,
            Long templateId,
            boolean usingFallback,
            String templateBody) {}
}
