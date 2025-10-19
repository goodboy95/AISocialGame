package com.aisocialgame.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RoomWebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketEventListener.class);

    private final RoomWebSocketHandler roomWebSocketHandler;

    public RoomWebSocketEventListener(RoomWebSocketHandler roomWebSocketHandler) {
        this.roomWebSocketHandler = roomWebSocketHandler;
    }

    @EventListener
    public void onRoomUpdated(RoomEvents.RoomUpdated event) {
        log.debug("Received RoomUpdated event for room {}", event.roomId());
        roomWebSocketHandler.broadcastRoomUpdate(event.roomId(), event.actor(), event.event(), event.message());
    }

    @EventListener
    public void onRoomRemoved(RoomEvents.RoomRemoved event) {
        log.debug("Received RoomRemoved event for room {}", event.roomId());
        roomWebSocketHandler.closeRoom(event.roomId(), event.reason());
    }
}
