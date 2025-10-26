package com.aisocialgame.backend.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ManageDtos {

    private ManageDtos() {}

    public record AdminAccess(
            @JsonProperty("is_admin") boolean isAdmin) {}

    public record AiModelConfigPayload(
            @NotBlank @Size(max = 100) String name,
            @JsonProperty("base_url") @NotBlank @Size(max = 255) String baseUrl,
            @NotBlank @Size(max = 255) String token) {}

    public record AiModelConfigView(
            long id,
            String name,
            @JsonProperty("base_url") String baseUrl,
            String token,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt) {}

    public record UndercoverAiRolePayload(
            @NotBlank @Size(max = 100) String name,
            @JsonProperty("model_id") @NotNull Long modelId,
            @NotBlank String personality) {}

    public record UndercoverAiRoleView(
            long id,
            String name,
            @JsonProperty("model") AiModelConfigSummary model,
            String personality,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt) {}

    public record AiModelConfigSummary(
            long id,
            String name) {}

    public record AiPromptTemplatePayload(
            @JsonProperty("game_type") @NotBlank @Size(max = 50) String gameType,
            @JsonProperty("role_key") @Size(max = 100) String roleKey,
            @JsonProperty("phase_key") @NotBlank @Size(max = 50) String phaseKey,
            @NotBlank String content) {}

    public record AiPromptTemplateView(
            long id,
            @JsonProperty("game_type") String gameType,
            @JsonProperty("role_key") String roleKey,
            @JsonProperty("phase_key") String phaseKey,
            String content,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt) {}

    public record AiPromptDictionary(
            List<AiPromptGameOption> games,
            @JsonProperty("default_role_key") String defaultRoleKey) {}

    public record AiPromptGameOption(
            String key,
            String label,
            List<AiPromptPhaseOption> phases,
            List<AiPromptRoleOption> roles) {}

    public record AiPromptPhaseOption(
            String key,
            String label) {}

    public record AiPromptRoleOption(
            String key,
            String label) {}

    public record ManageOverview(
            List<AiModelConfigView> models,
            List<UndercoverAiRoleView> undercoverRoles) {}
}
