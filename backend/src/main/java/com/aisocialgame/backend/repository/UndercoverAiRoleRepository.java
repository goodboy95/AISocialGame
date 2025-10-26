package com.aisocialgame.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aisocialgame.backend.entity.UndercoverAiRole;

public interface UndercoverAiRoleRepository extends JpaRepository<UndercoverAiRole, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<UndercoverAiRole> findByNameIgnoreCase(String name);
    boolean existsByModel_Id(Long modelId);
}
