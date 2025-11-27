package com.aisocialgame.dto;

import com.aisocialgame.model.GameLogEntry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class GameStateResponse {
    private String roomId;
    private String gameId;
    private String phase;
    private int round;
    private Integer currentSeat;
    private String currentSpeakerName;
    private String winner;
    private String myPlayerId;
    private Integer mySeatNumber;
    private String myWord;
    private String myRole;
    private LocalDateTime phaseEndsAt;
    private List<GamePlayerView> players;
    private List<GameLogEntry> logs;
    private Map<String, Object> extra;
    private Map<String, String> votes;
    private PendingAction pendingAction;

    public GameStateResponse(String roomId, String gameId, String phase, int round, Integer currentSeat, String currentSpeakerName, String winner, String myPlayerId, Integer mySeatNumber, String myWord, String myRole, LocalDateTime phaseEndsAt, List<GamePlayerView> players, List<GameLogEntry> logs, Map<String, Object> extra, Map<String, String> votes, PendingAction pendingAction) {
        this.roomId = roomId;
        this.gameId = gameId;
        this.phase = phase;
        this.round = round;
        this.currentSeat = currentSeat;
        this.currentSpeakerName = currentSpeakerName;
        this.winner = winner;
        this.myPlayerId = myPlayerId;
        this.mySeatNumber = mySeatNumber;
        this.myWord = myWord;
        this.myRole = myRole;
        this.phaseEndsAt = phaseEndsAt;
        this.players = players;
        this.logs = logs;
        this.extra = extra;
        this.votes = votes;
        this.pendingAction = pendingAction;
    }

    public String getRoomId() { return roomId; }
    public String getGameId() { return gameId; }
    public String getPhase() { return phase; }
    public int getRound() { return round; }
    public Integer getCurrentSeat() { return currentSeat; }
    public String getCurrentSpeakerName() { return currentSpeakerName; }
    public String getWinner() { return winner; }
    public String getMyPlayerId() { return myPlayerId; }
    public Integer getMySeatNumber() { return mySeatNumber; }
    public String getMyWord() { return myWord; }
    public String getMyRole() { return myRole; }
    public LocalDateTime getPhaseEndsAt() { return phaseEndsAt; }
    public List<GamePlayerView> getPlayers() { return players; }
    public List<GameLogEntry> getLogs() { return logs; }
    public Map<String, Object> getExtra() { return extra; }
    public Map<String, String> getVotes() { return votes; }
    public PendingAction getPendingAction() { return pendingAction; }
}
