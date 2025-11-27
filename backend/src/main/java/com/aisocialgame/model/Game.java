package com.aisocialgame.model;

import java.util.List;

public class Game {
    private String id;
    private String name;
    private String description;
    private String coverUrl;
    private List<String> tags;
    private int minPlayers;
    private int maxPlayers;
    private GameStatus status;
    private int onlineCount;
    private List<GameConfigOption> configSchema;

    public Game() {}

    public Game(String id, String name, String description, String coverUrl, List<String> tags,
                int minPlayers, int maxPlayers, GameStatus status, int onlineCount, List<GameConfigOption> configSchema) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.coverUrl = coverUrl;
        this.tags = tags;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
        this.onlineCount = onlineCount;
        this.configSchema = configSchema;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public List<String> getTags() {
        return tags;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public GameStatus getStatus() {
        return status;
    }

    public int getOnlineCount() {
        return onlineCount;
    }

    public void setOnlineCount(int onlineCount) {
        this.onlineCount = onlineCount;
    }

    public List<GameConfigOption> getConfigSchema() {
        return configSchema;
    }
}
