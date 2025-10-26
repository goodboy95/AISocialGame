package com.aisocialgame.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aisocialgame.backend.dto.ManageDtos;
import com.aisocialgame.backend.entity.AiModelConfig;
import com.aisocialgame.backend.entity.AiPromptTemplate;
import com.aisocialgame.backend.entity.UndercoverAiRole;
import com.aisocialgame.backend.repository.AiModelConfigRepository;
import com.aisocialgame.backend.repository.AiPromptTemplateRepository;
import com.aisocialgame.backend.repository.UndercoverAiRoleRepository;

@Service
@Transactional(readOnly = true)
public class ManageService {

    private final AiModelConfigRepository aiModelConfigRepository;
    private final UndercoverAiRoleRepository undercoverAiRoleRepository;
    private final AiPromptTemplateRepository aiPromptTemplateRepository;

    public ManageService(
            AiModelConfigRepository aiModelConfigRepository,
            UndercoverAiRoleRepository undercoverAiRoleRepository,
            AiPromptTemplateRepository aiPromptTemplateRepository) {
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.undercoverAiRoleRepository = undercoverAiRoleRepository;
        this.aiPromptTemplateRepository = aiPromptTemplateRepository;
    }

    public List<ManageDtos.AiModelConfigView> listModelConfigs() {
        return aiModelConfigRepository.findAll().stream()
                .sorted(Comparator.comparing(AiModelConfig::getCreatedAt))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public ManageDtos.AiModelConfigView createModelConfig(ManageDtos.AiModelConfigPayload payload) {
        validateModelName(payload.name(), null);
        AiModelConfig config = new AiModelConfig();
        config.setName(payload.name().trim());
        config.setBaseUrl(payload.baseUrl().trim());
        config.setToken(payload.token().trim());
        return toView(aiModelConfigRepository.save(config));
    }

    @Transactional
    public ManageDtos.AiModelConfigView updateModelConfig(long id, ManageDtos.AiModelConfigPayload payload) {
        AiModelConfig config = aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到对应的模型配置"));
        validateModelName(payload.name(), id);
        config.setName(payload.name().trim());
        config.setBaseUrl(payload.baseUrl().trim());
        config.setToken(payload.token().trim());
        return toView(aiModelConfigRepository.save(config));
    }

    @Transactional
    public void deleteModelConfig(long id) {
        AiModelConfig config = aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到对应的模型配置"));
        if (undercoverAiRoleRepository.existsByModel_Id(id)) {
            throw new IllegalStateException("该模型仍被 AI 角色使用，无法删除");
        }
        aiModelConfigRepository.delete(config);
    }

    public List<ManageDtos.UndercoverAiRoleView> listUndercoverRoles() {
        return undercoverAiRoleRepository.findAll().stream()
                .sorted(Comparator.comparing(UndercoverAiRole::getCreatedAt))
                .map(this::toRoleView)
                .toList();
    }

    @Transactional
    public ManageDtos.UndercoverAiRoleView createUndercoverRole(ManageDtos.UndercoverAiRolePayload payload) {
        validateRoleName(payload.name(), null);
        AiModelConfig model = aiModelConfigRepository.findById(payload.modelId())
                .orElseThrow(() -> new IllegalArgumentException("关联的模型配置不存在"));
        UndercoverAiRole role = new UndercoverAiRole();
        role.setName(payload.name().trim());
        role.setModel(model);
        role.setPersonality(payload.personality().trim());
        return toRoleView(undercoverAiRoleRepository.save(role));
    }

    @Transactional
    public ManageDtos.UndercoverAiRoleView updateUndercoverRole(long id, ManageDtos.UndercoverAiRolePayload payload) {
        UndercoverAiRole role = undercoverAiRoleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到对应的 AI 角色"));
        validateRoleName(payload.name(), id);
        AiModelConfig model = aiModelConfigRepository.findById(payload.modelId())
                .orElseThrow(() -> new IllegalArgumentException("关联的模型配置不存在"));
        role.setName(payload.name().trim());
        role.setModel(model);
        role.setPersonality(payload.personality().trim());
        return toRoleView(undercoverAiRoleRepository.save(role));
    }

    @Transactional
    public void deleteUndercoverRole(long id) {
        if (!undercoverAiRoleRepository.existsById(id)) {
            throw new IllegalArgumentException("未找到对应的 AI 角色");
        }
        undercoverAiRoleRepository.deleteById(id);
    }

    public List<ManageDtos.AiPromptTemplateView> listPromptTemplates(String gameType, String roleKey, String phaseKey) {
        return aiPromptTemplateRepository.findAll().stream()
                .filter(template -> filterTemplate(template, gameType, roleKey, phaseKey))
                .sorted(Comparator
                        .comparing(AiPromptTemplate::getGameType)
                        .thenComparing(AiPromptTemplate::getRoleKey)
                        .thenComparing(AiPromptTemplate::getPhaseKey)
                        .thenComparing(AiPromptTemplate::getCreatedAt))
                .map(this::toPromptView)
                .toList();
    }

    @Transactional
    public ManageDtos.AiPromptTemplateView createPromptTemplate(ManageDtos.AiPromptTemplatePayload payload) {
        String normalizedGame = normalizeKey(payload.gameType());
        String normalizedRole = normalizeRoleKey(payload.roleKey());
        String normalizedPhase = normalizeKey(payload.phaseKey());
        validatePromptUniqueness(normalizedGame, normalizedRole, normalizedPhase, null);

        AiPromptTemplate template = new AiPromptTemplate();
        template.setGameType(normalizedGame);
        template.setRoleKey(normalizedRole);
        template.setPhaseKey(normalizedPhase);
        template.setContentTemplate(payload.content().trim());
        return toPromptView(aiPromptTemplateRepository.save(template));
    }

    @Transactional
    public ManageDtos.AiPromptTemplateView updatePromptTemplate(long id, ManageDtos.AiPromptTemplatePayload payload) {
        AiPromptTemplate template = aiPromptTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到对应的提示词配置"));
        String normalizedGame = normalizeKey(payload.gameType());
        String normalizedRole = normalizeRoleKey(payload.roleKey());
        String normalizedPhase = normalizeKey(payload.phaseKey());
        validatePromptUniqueness(normalizedGame, normalizedRole, normalizedPhase, id);

        template.setGameType(normalizedGame);
        template.setRoleKey(normalizedRole);
        template.setPhaseKey(normalizedPhase);
        template.setContentTemplate(payload.content().trim());
        return toPromptView(aiPromptTemplateRepository.save(template));
    }

    @Transactional
    public void deletePromptTemplate(long id) {
        if (!aiPromptTemplateRepository.existsById(id)) {
            throw new IllegalArgumentException("未找到对应的提示词配置");
        }
        aiPromptTemplateRepository.deleteById(id);
    }

    public ManageDtos.AiPromptDictionary promptDictionary() {
        Map<String, PromptOptionAccumulator> accumulators = new LinkedHashMap<>();
        for (ManageDtos.AiPromptGameOption option : defaultGameOptions()) {
            accumulators.put(option.key(), PromptOptionAccumulator.from(option));
        }

        aiPromptTemplateRepository.findAll().forEach(template -> {
            PromptOptionAccumulator accumulator = accumulators.computeIfAbsent(
                    template.getGameType(),
                    key -> PromptOptionAccumulator.createDynamic(key));
            accumulator.addPhase(template.getPhaseKey());
            accumulator.addRole(template.getRoleKey());
        });

        List<ManageDtos.AiPromptGameOption> options = accumulators.values().stream()
                .map(PromptOptionAccumulator::toRecord)
                .toList();
        return new ManageDtos.AiPromptDictionary(options, AiPromptService.ROLE_GENERAL);
    }

    private boolean filterTemplate(AiPromptTemplate template, String gameType, String roleKey, String phaseKey) {
        boolean gameMatches = !StringUtils.hasText(gameType)
                || template.getGameType().equalsIgnoreCase(gameType);
        boolean roleMatches = !StringUtils.hasText(roleKey)
                || template.getRoleKey().equalsIgnoreCase(roleKey);
        boolean phaseMatches = !StringUtils.hasText(phaseKey)
                || template.getPhaseKey().equalsIgnoreCase(phaseKey);
        return gameMatches && roleMatches && phaseMatches;
    }

    private void validateModelName(String name, Long currentId) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("配置名不能为空");
        }
        aiModelConfigRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("配置名已存在");
            }
        });
    }

    private void validateRoleName(String name, Long currentId) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        undercoverAiRoleRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("角色名称已存在");
            }
        });
    }

    private ManageDtos.AiModelConfigView toView(AiModelConfig config) {
        return new ManageDtos.AiModelConfigView(
                config.getId(),
                config.getName(),
                config.getBaseUrl(),
                config.getToken(),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }

    private ManageDtos.AiModelConfigSummary toSummary(AiModelConfig config) {
        return new ManageDtos.AiModelConfigSummary(config.getId(), config.getName());
    }

    private ManageDtos.UndercoverAiRoleView toRoleView(UndercoverAiRole role) {
        return new ManageDtos.UndercoverAiRoleView(
                role.getId(),
                role.getName(),
                toSummary(role.getModel()),
                role.getPersonality(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    private ManageDtos.AiPromptTemplateView toPromptView(AiPromptTemplate template) {
        return new ManageDtos.AiPromptTemplateView(
                template.getId(),
                template.getGameType(),
                template.getRoleKey(),
                template.getPhaseKey(),
                template.getContentTemplate(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private void validatePromptUniqueness(String gameType, String roleKey, String phaseKey, Long currentId) {
        aiPromptTemplateRepository.findByGameTypeAndRoleKeyAndPhaseKey(gameType, roleKey, phaseKey)
                .ifPresent(existing -> {
                    if (currentId == null || !Objects.equals(existing.getId(), currentId)) {
                        throw new IllegalArgumentException("该游戏/角色/阶段组合的提示词已存在");
                    }
                });
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("关键字段不能为空");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRoleKey(String value) {
        if (!StringUtils.hasText(value)) {
            return AiPromptService.ROLE_GENERAL;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private List<ManageDtos.AiPromptGameOption> defaultGameOptions() {
        return List.of(
                new ManageDtos.AiPromptGameOption(
                        AiPromptService.GAME_WHO_IS_UNDERCOVER,
                        "谁是卧底",
                        List.of(
                                new ManageDtos.AiPromptPhaseOption(AiPromptService.PHASE_SPEECH, "发言阶段"),
                                new ManageDtos.AiPromptPhaseOption(AiPromptService.PHASE_VOTE, "投票阶段")),
                        List.of(
                                new ManageDtos.AiPromptRoleOption(AiPromptService.ROLE_GENERAL, "通用"))),
                new ManageDtos.AiPromptGameOption(
                        AiPromptService.GAME_WEREWOLF,
                        "狼人杀",
                        List.of(
                                new ManageDtos.AiPromptPhaseOption(AiPromptService.PHASE_NIGHT_ACTION, "天黑行动阶段"),
                                new ManageDtos.AiPromptPhaseOption(AiPromptService.PHASE_DAY_DISCUSSION, "天亮发言阶段"),
                                new ManageDtos.AiPromptPhaseOption(AiPromptService.PHASE_VOTE, "投票阶段")),
                        List.of(
                                new ManageDtos.AiPromptRoleOption("villager", "村民"),
                                new ManageDtos.AiPromptRoleOption("seer", "预言家"),
                                new ManageDtos.AiPromptRoleOption("witch", "女巫"),
                                new ManageDtos.AiPromptRoleOption("hunter", "猎人"),
                                new ManageDtos.AiPromptRoleOption("werewolf", "狼人"),
                                new ManageDtos.AiPromptRoleOption(AiPromptService.ROLE_GENERAL, "通用"))));
    }

    private record PromptOptionAccumulator(
            String key,
            String label,
            Set<ManageDtos.AiPromptPhaseOption> phases,
            Set<ManageDtos.AiPromptRoleOption> roles) {

        static PromptOptionAccumulator from(ManageDtos.AiPromptGameOption option) {
            return new PromptOptionAccumulator(
                    option.key(),
                    option.label(),
                    new LinkedHashSet<>(option.phases()),
                    new LinkedHashSet<>(option.roles()));
        }

        static PromptOptionAccumulator createDynamic(String gameKey) {
            String label = switch (gameKey) {
                case AiPromptService.GAME_WHO_IS_UNDERCOVER -> "谁是卧底";
                case AiPromptService.GAME_WEREWOLF -> "狼人杀";
                default -> gameKey;
            };
            return new PromptOptionAccumulator(
                    gameKey,
                    label,
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(List.of(new ManageDtos.AiPromptRoleOption(AiPromptService.ROLE_GENERAL, "通用"))));
        }

        void addPhase(String phaseKey) {
            phases.add(new ManageDtos.AiPromptPhaseOption(phaseKey, phaseLabel(phaseKey)));
        }

        void addRole(String roleKey) {
            roles.add(new ManageDtos.AiPromptRoleOption(roleKey, roleLabel(roleKey)));
        }

        private String phaseLabel(String phaseKey) {
            return switch (phaseKey) {
                case AiPromptService.PHASE_SPEECH -> "发言阶段";
                case AiPromptService.PHASE_VOTE -> "投票阶段";
                case AiPromptService.PHASE_NIGHT_ACTION -> "天黑行动阶段";
                case AiPromptService.PHASE_DAY_DISCUSSION -> "天亮发言阶段";
                default -> phaseKey;
            };
        }

        private String roleLabel(String roleKey) {
            return switch (roleKey) {
                case "villager" -> "村民";
                case "seer" -> "预言家";
                case "witch" -> "女巫";
                case "hunter" -> "猎人";
                case "werewolf" -> "狼人";
                case AiPromptService.ROLE_GENERAL -> "通用";
                default -> roleKey;
            };
        }

        ManageDtos.AiPromptGameOption toRecord() {
            return new ManageDtos.AiPromptGameOption(
                    key,
                    label,
                    new ArrayList<>(phases),
                    new ArrayList<>(roles));
        }
    }
}
