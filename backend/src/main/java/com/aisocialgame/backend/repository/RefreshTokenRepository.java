package com.aisocialgame.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aisocialgame.backend.entity.RefreshToken;
import com.aisocialgame.backend.entity.UserAccount;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(UserAccount user);

    void deleteByExpiresAtBefore(Instant instant);
}
