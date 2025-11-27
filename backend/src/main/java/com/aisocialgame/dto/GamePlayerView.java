package com.aisocialgame.dto;

public class GamePlayerView {
    private String playerId;
    private String displayName;
    private int seatNumber;
    private boolean ai;
    private String personaId;
    private String avatar;
    private boolean alive;
    private String role;
    private String word;

    public GamePlayerView() {}

    public GamePlayerView(String playerId, String displayName, int seatNumber, boolean ai, String personaId, String avatar, boolean alive, String role, String word) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.seatNumber = seatNumber;
        this.ai = ai;
        this.personaId = personaId;
        this.avatar = avatar;
        this.alive = alive;
        this.role = role;
        this.word = word;
    }

    public String getPlayerId() { return playerId; }
    public String getDisplayName() { return displayName; }
    public int getSeatNumber() { return seatNumber; }
    public boolean isAi() { return ai; }
    public String getPersonaId() { return personaId; }
    public String getAvatar() { return avatar; }
    public boolean isAlive() { return alive; }
    public String getRole() { return role; }
    public String getWord() { return word; }

    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }
    public void setAi(boolean ai) { this.ai = ai; }
    public void setPersonaId(String personaId) { this.personaId = personaId; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public void setRole(String role) { this.role = role; }
    public void setWord(String word) { this.word = word; }
}
