package com.aisocialgame.backend.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RoomRealtimeListener {

    private static final Logger log = LoggerFactory.getLogger(RoomRealtimeListener.class);

    private final RoomSocketCoordinator coordinator;

    public RoomRealtimeListener(RoomSocketCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @EventListener
    public void onRoomUpdated(RoomRealtimeEvents.RoomUpdated event) {
        coordinator.resolveRoom(event.roomId()).ifPresent(room -> {
            log.debug("Broadcasting update for room {} via websocket", room.getId());
            coordinator.broadcastRoomState(room, event.actor(), event.event(), event.message());
        });
    }

    @EventListener
    public void onRoomRemoved(RoomRealtimeEvents.RoomRemoved event) {
        log.debug("Closing websocket connections for removed room {}", event.roomId());
        coordinator.closeRoom(event.roomId(), event.reason());
    }
}
