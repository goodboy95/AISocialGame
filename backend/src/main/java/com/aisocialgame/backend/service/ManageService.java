package com.aisocialgame.backend.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aisocialgame.backend.dto.ManageDtos;
import com.aisocialgame.backend.entity.AiModelConfig;
import com.aisocialgame.backend.entity.UndercoverAiRole;
import com.aisocialgame.backend.repository.AiModelConfigRepository;
import com.aisocialgame.backend.repository.UndercoverAiRoleRepository;

@Service
@Transactional(readOnly = true)
public class ManageService {

    private final AiModelConfigRepository aiModelConfigRepository;
    private final UndercoverAiRoleRepository undercoverAiRoleRepository;

    public ManageService(
            AiModelConfigRepository aiModelConfigRepository,
            UndercoverAiRoleRepository undercoverAiRoleRepository) {
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.undercoverAiRoleRepository = undercoverAiRoleRepository;
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
}
