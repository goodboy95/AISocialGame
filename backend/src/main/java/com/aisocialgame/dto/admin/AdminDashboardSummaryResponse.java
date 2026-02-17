package com.aisocialgame.dto.admin;

public class AdminDashboardSummaryResponse {
    private long localUsers;
    private long localRooms;
    private long localPosts;
    private long localGameStates;
    private int aiModels;

    public AdminDashboardSummaryResponse(long localUsers, long localRooms, long localPosts, long localGameStates, int aiModels) {
        this.localUsers = localUsers;
        this.localRooms = localRooms;
        this.localPosts = localPosts;
        this.localGameStates = localGameStates;
        this.aiModels = aiModels;
    }

    public long getLocalUsers() {
        return localUsers;
    }

    public long getLocalRooms() {
        return localRooms;
    }

    public long getLocalPosts() {
        return localPosts;
    }

    public long getLocalGameStates() {
        return localGameStates;
    }

    public int getAiModels() {
        return aiModels;
    }
}
