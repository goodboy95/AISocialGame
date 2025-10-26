package com.aisocialgame.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ai_prompt_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_prompt_game_role_phase", columnNames = { "game_type", "role_key", "phase_key" })
})
public class AiPromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @Column(name = "role_key", nullable = false, length = 100)
    private String roleKey;

    @Column(name = "phase_key", nullable = false, length = 50)
    private String phaseKey;

    @Column(name = "content_template", nullable = false, columnDefinition = "TEXT")
    private String contentTemplate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (roleKey == null || roleKey.isBlank()) {
            roleKey = "general";
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        if (roleKey == null || roleKey.isBlank()) {
            roleKey = "general";
        }
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public String getPhaseKey() {
        return phaseKey;
    }

    public void setPhaseKey(String phaseKey) {
        this.phaseKey = phaseKey;
    }

    public String getContentTemplate() {
        return contentTemplate;
    }

    public void setContentTemplate(String contentTemplate) {
        this.contentTemplate = contentTemplate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
