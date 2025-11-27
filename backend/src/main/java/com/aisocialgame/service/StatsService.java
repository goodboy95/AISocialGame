package com.aisocialgame.service;

import com.aisocialgame.model.GamePlayerState;
import com.aisocialgame.model.PlayerStats;
import com.aisocialgame.model.User;
import com.aisocialgame.repository.PlayerStatsRepository;
import com.aisocialgame.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class StatsService {
    private final PlayerStatsRepository playerStatsRepository;
    private final UserRepository userRepository;

    public StatsService(PlayerStatsRepository playerStatsRepository, UserRepository userRepository) {
        this.playerStatsRepository = playerStatsRepository;
        this.userRepository = userRepository;
    }

    public void recordResult(String gameId, List<GamePlayerState> players, Set<String> winnerIds) {
        Set<String> humanIds = new HashSet<>();
        for (GamePlayerState player : players) {
            if (player.isAi() || player.getPlayerId() == null) {
                continue;
            }
            humanIds.add(player.getPlayerId());
            boolean win = winnerIds.contains(player.getPlayerId());
            upsertStats(gameId, player, win);
            upsertStats("total", player, win);
            rewardCoins(player.getPlayerId(), win);
        }
    }

    public List<PlayerStats> top(String gameId) {
        return playerStatsRepository.findTop20ByGameIdOrderByScoreDesc(gameId);
    }

    private void upsertStats(String gameId, GamePlayerState player, boolean win) {
        Optional<PlayerStats> existing = playerStatsRepository.findByPlayerIdAndGameId(player.getPlayerId(), gameId);
        PlayerStats stats = existing.orElseGet(() -> {
            PlayerStats ps = new PlayerStats();
            ps.setId(player.getPlayerId() + ":" + gameId);
            ps.setPlayerId(player.getPlayerId());
            ps.setGameId(gameId);
            return ps;
        });
        stats.setDisplayName(player.getDisplayName());
        stats.setAvatar(player.getAvatar());
        stats.setGamesPlayed(stats.getGamesPlayed() + 1);
        if (win) {
            stats.setWins(stats.getWins() + 1);
            stats.setScore(stats.getScore() + 15);
        } else {
            stats.setScore(stats.getScore() + 5);
        }
        playerStatsRepository.save(stats);
    }

    private void rewardCoins(String playerId, boolean win) {
        userRepository.findById(playerId).ifPresent(user -> {
            int bonus = win ? 30 : 8;
            user.setCoins(user.getCoins() + bonus);
            userRepository.save(user);
        });
    }
}
