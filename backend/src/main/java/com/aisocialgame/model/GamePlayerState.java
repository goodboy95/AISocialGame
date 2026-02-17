package com.aisocialgame.model;

import java.time.LocalDateTime;

public class GamePlayerState {
    private String playerId;
    private String displayName;
    private int seatNumber;
    private boolean ai;
    private String personaId;
    private String avatar;
    private String role;
    private String word;
    private boolean alive;
    private String connectionStatus;
    private LocalDateTime lastActiveAt;
    private LocalDateTime disconnectedAt;

    public GamePlayerState() {}

    public GamePlayerState(String playerId, String displayName, int seatNumber, boolean ai, String personaId, String avatar) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.seatNumber = seatNumber;
        this.ai = ai;
        this.personaId = personaId;
        this.avatar = avatar;
        this.alive = true;
    }

    public String getPlayerId() { return playerId; }
    public String getDisplayName() { return displayName; }
    public int getSeatNumber() { return seatNumber; }
    public boolean isAi() { return ai; }
    public String getPersonaId() { return personaId; }
    public String getAvatar() { return avatar; }
    public String getRole() { return role; }
    public String getWord() { return word; }
    public boolean isAlive() { return alive; }
    public String getConnectionStatus() { return connectionStatus; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public LocalDateTime getDisconnectedAt() { return disconnectedAt; }

    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }
    public void setAi(boolean ai) { this.ai = ai; }
    public void setPersonaId(String personaId) { this.personaId = personaId; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public void setRole(String role) { this.role = role; }
    public void setWord(String word) { this.word = word; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public void setConnectionStatus(String connectionStatus) { this.connectionStatus = connectionStatus; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public void setDisconnectedAt(LocalDateTime disconnectedAt) { this.disconnectedAt = disconnectedAt; }
}
