package com.aisocialgame.repository;

import com.aisocialgame.model.PlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerStatsRepository extends JpaRepository<PlayerStats, String> {
    List<PlayerStats> findTop20ByGameIdOrderByScoreDesc(String gameId);
    Optional<PlayerStats> findByPlayerIdAndGameId(String playerId, String gameId);
}
