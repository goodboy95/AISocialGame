package com.aisocialgame.backend.realtime;

public final class RoomRealtimeEvents {

    private RoomRealtimeEvents() {
    }

    public record Actor(Long playerId, Long userId, String username, String displayName) {
    }

    public record RoomUpdated(long roomId, Actor actor, String event, String message) {
    }

    public record RoomRemoved(long roomId, String reason) {
    }
}
