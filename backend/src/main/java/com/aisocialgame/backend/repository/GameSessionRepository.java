package com.aisocialgame.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aisocialgame.backend.entity.GameSession;
import com.aisocialgame.backend.entity.Room;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findFirstByRoomOrderByStartedAtDesc(Room room);
}
