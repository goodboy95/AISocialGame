package com.aisocialgame.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_stats")
public class PlayerStats {
    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 36)
    private String playerId;

    @Column(nullable = false, length = 32)
    private String gameId;

    @Column(nullable = false, length = 64)
    private String displayName;

    private String avatar;

    private int gamesPlayed;
    private int wins;
    private int score;

    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getPlayerId() { return playerId; }
    public String getGameId() { return gameId; }
    public String getDisplayName() { return displayName; }
    public String getAvatar() { return avatar; }
    public int getGamesPlayed() { return gamesPlayed; }
    public int getWins() { return wins; }
    public int getScore() { return score; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public void setGamesPlayed(int gamesPlayed) { this.gamesPlayed = gamesPlayed; }
    public void setWins(int wins) { this.wins = wins; }
    public void setScore(int score) { this.score = score; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
