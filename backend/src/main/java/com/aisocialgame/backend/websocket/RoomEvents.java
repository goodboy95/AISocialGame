package com.aisocialgame.backend.websocket;

public final class RoomEvents {

    private RoomEvents() {
    }

    public record ActorSnapshot(Long playerId, Long userId, String username, String displayName) {
    }

    public record RoomUpdated(long roomId, ActorSnapshot actor, String event, String message) {
    }

    public record RoomRemoved(long roomId, String reason) {
    }
}
