package com.aisocialgame.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aisocialgame.backend.entity.AiPromptTemplate;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {
    List<AiPromptTemplate> findByGameTypeOrderByRoleKeyAscPhaseKeyAsc(String gameType);

    List<AiPromptTemplate> findByGameTypeAndRoleKeyOrderByPhaseKeyAsc(String gameType, String roleKey);

    Optional<AiPromptTemplate> findByGameTypeAndRoleKeyAndPhaseKey(String gameType, String roleKey, String phaseKey);
}
