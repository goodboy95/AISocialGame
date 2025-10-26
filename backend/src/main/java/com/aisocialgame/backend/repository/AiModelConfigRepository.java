package com.aisocialgame.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aisocialgame.backend.entity.AiModelConfig;

public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {
    Optional<AiModelConfig> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
