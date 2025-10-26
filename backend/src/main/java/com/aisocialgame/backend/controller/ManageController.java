package com.aisocialgame.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aisocialgame.backend.dto.ManageDtos;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.service.AuthService;
import com.aisocialgame.backend.service.ManageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/manage")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class ManageController {

    private final ManageService manageService;
    private final AuthService authService;

    public ManageController(ManageService manageService, AuthService authService) {
        this.manageService = manageService;
        this.authService = authService;
    }

    @GetMapping("/access/")
    public ResponseEntity<ManageDtos.AdminAccess> access() {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new ManageDtos.AdminAccess(current.isAdmin()));
    }

    @GetMapping("/ai-models/")
    public List<ManageDtos.AiModelConfigView> listModels() {
        return manageService.listModelConfigs();
    }

    @GetMapping("/overview/")
    public ManageDtos.ManageOverview overview() {
        return new ManageDtos.ManageOverview(
                manageService.listModelConfigs(),
                manageService.listUndercoverRoles());
    }

    @PostMapping("/ai-models/")
    public ResponseEntity<ManageDtos.AiModelConfigView> createModel(
            @Valid @RequestBody ManageDtos.AiModelConfigPayload payload) {
        return ResponseEntity.ok(manageService.createModelConfig(payload));
    }

    @PatchMapping("/ai-models/{id}/")
    public ResponseEntity<ManageDtos.AiModelConfigView> updateModel(
            @PathVariable("id") long id,
            @Valid @RequestBody ManageDtos.AiModelConfigPayload payload) {
        return ResponseEntity.ok(manageService.updateModelConfig(id, payload));
    }

    @DeleteMapping("/ai-models/{id}/")
    public ResponseEntity<Void> deleteModel(@PathVariable("id") long id) {
        manageService.deleteModelConfig(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/undercover/roles/")
    public List<ManageDtos.UndercoverAiRoleView> listUndercoverRoles() {
        return manageService.listUndercoverRoles();
    }

    @PostMapping("/undercover/roles/")
    public ResponseEntity<ManageDtos.UndercoverAiRoleView> createUndercoverRole(
            @Valid @RequestBody ManageDtos.UndercoverAiRolePayload payload) {
        return ResponseEntity.ok(manageService.createUndercoverRole(payload));
    }

    @PatchMapping("/undercover/roles/{id}/")
    public ResponseEntity<ManageDtos.UndercoverAiRoleView> updateUndercoverRole(
            @PathVariable("id") long id,
            @Valid @RequestBody ManageDtos.UndercoverAiRolePayload payload) {
        return ResponseEntity.ok(manageService.updateUndercoverRole(id, payload));
    }

    @DeleteMapping("/undercover/roles/{id}/")
    public ResponseEntity<Void> deleteUndercoverRole(@PathVariable("id") long id) {
        manageService.deleteUndercoverRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/prompts/")
    public List<ManageDtos.AiPromptTemplateView> listPrompts(
            @RequestParam(value = "game_type", required = false) String gameType,
            @RequestParam(value = "role_key", required = false) String roleKey,
            @RequestParam(value = "phase_key", required = false) String phaseKey) {
        return manageService.listPromptTemplates(gameType, roleKey, phaseKey);
    }

    @GetMapping("/prompts/dictionary/")
    public ManageDtos.AiPromptDictionary promptDictionary() {
        return manageService.promptDictionary();
    }

    @PostMapping("/prompts/")
    public ResponseEntity<ManageDtos.AiPromptTemplateView> createPrompt(
            @Valid @RequestBody ManageDtos.AiPromptTemplatePayload payload) {
        return ResponseEntity.ok(manageService.createPromptTemplate(payload));
    }

    @PatchMapping("/prompts/{id}/")
    public ResponseEntity<ManageDtos.AiPromptTemplateView> updatePrompt(
            @PathVariable("id") long id,
            @Valid @RequestBody ManageDtos.AiPromptTemplatePayload payload) {
        return ResponseEntity.ok(manageService.updatePromptTemplate(id, payload));
    }

    @DeleteMapping("/prompts/{id}/")
    public ResponseEntity<Void> deletePrompt(@PathVariable("id") long id) {
        manageService.deletePromptTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
