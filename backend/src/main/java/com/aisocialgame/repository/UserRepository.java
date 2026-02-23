package com.aisocialgame.repository;

import com.aisocialgame.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByExternalUserId(Long externalUserId);
    boolean existsByEmail(String email);
    Page<User> findByExternalUserIdGreaterThan(Long externalUserId, Pageable pageable);
}
