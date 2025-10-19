package com.aisocialgame.backend.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.aisocialgame.backend.dto.RoomDtos;
import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.repository.RoomPlayerRepository;
import com.aisocialgame.backend.service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RoomSocketCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RoomSocketCoordinator.class);

    private final Map<Long, Set<RoomSocketClient>> activeRooms = new ConcurrentHashMap<>();
    private final RoomService roomService;
    private final RoomPlayerRepository roomPlayerRepository;
    private final ObjectMapper objectMapper;

    public RoomSocketCoordinator(RoomService roomService, RoomPlayerRepository roomPlayerRepository, ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.roomPlayerRepository = roomPlayerRepository;
        this.objectMapper = objectMapper;
    }

    public RoomSocketClient register(Room room, UserAccount user, WebSocketSession session) {
        RoomSocketClient client = new RoomSocketClient(session, room, user);
        client.refreshPlayer(room, roomPlayerRepository);
        activeRooms.computeIfAbsent(room.getId(), key -> new CopyOnWriteArraySet<>()).add(client);
        log.info("Registered websocket session {} for room {}", session.getId(), room.getId());
        return client;
    }

    public RoomSocketClient unregister(WebSocketSession session) {
        for (Map.Entry<Long, Set<RoomSocketClient>> entry : activeRooms.entrySet()) {
            Set<RoomSocketClient> clients = entry.getValue();
            for (RoomSocketClient client : clients) {
                if (client.session.equals(session)) {
                    clients.remove(client);
                    if (clients.isEmpty()) {
                        activeRooms.remove(entry.getKey());
                    }
                    log.info("Removed websocket session {} from room {}", session.getId(), client.roomId);
                    return client;
                }
            }
        }
        return null;
    }

    public RoomSocketClient find(WebSocketSession session) {
        for (Set<RoomSocketClient> clients : activeRooms.values()) {
            for (RoomSocketClient client : clients) {
                if (client.session.equals(session)) {
                    return client;
                }
            }
        }
        return null;
    }

    public Optional<Room> resolveRoom(long roomId) {
        return roomService.findById(roomId);
    }

    public void sendInitialSnapshot(RoomSocketClient client, Room room) {
        RoomDtos.RoomDetail detail = roomService.toRoomDetail(room, client.user);
        Map<String, Object> payload = Map.of(
                "type", "system.sync",
                "payload", detail);
        transmit(client.session, payload);
    }

    public void broadcastRoomState(Room room, RoomRealtimeEvents.Actor actor, String event, String message) {
        Set<RoomSocketClient> clients = activeRooms.getOrDefault(room.getId(), Collections.emptySet());
        if (clients.isEmpty()) {
            return;
        }
        Map<String, Object> actorPayload = resolveActor(actor);
        Instant now = Instant.now();
        for (RoomSocketClient client : clients) {
            client.refreshPlayer(room, roomPlayerRepository);
            RoomDtos.RoomDetail detail = roomService.toRoomDetail(room, client.user);
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("type", "system.broadcast");
            Map<String, Object> body = new HashMap<>();
            body.put("room", detail);
            body.put("timestamp", now.toString());
            if (StringUtils.hasText(event)) {
                body.put("event", event);
            }
            if (StringUtils.hasText(message)) {
                body.put("message", message);
            }
            if (actorPayload != null) {
                body.put("actor", actorPayload);
            }
            envelope.put("payload", body);
            transmit(client.session, envelope);
        }
    }

    public void closeRoom(long roomId, String reason) {
        Set<RoomSocketClient> clients = activeRooms.remove(roomId);
        if (clients == null || clients.isEmpty()) {
            return;
        }
        CloseStatus status = new CloseStatus(4000, StringUtils.hasText(reason) ? reason : "room closed");
        for (RoomSocketClient client : clients) {
            try {
                client.session.close(status);
            } catch (IOException ex) {
                log.warn("Failed to close websocket session {} for room {}", client.session.getId(), roomId, ex);
            }
        }
    }

    public void publishPublicChat(RoomSocketClient client, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Room room = resolveRoom(client.roomId).orElse(null);
        if (room == null) {
            return;
        }
        client.refreshPlayer(room, roomPlayerRepository);
        RoomPlayer sender = client.player;
        if (sender == null) {
            return;
        }
        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", UUID.randomUUID().toString());
        messagePayload.put("content", content);
        messagePayload.put("timestamp", Instant.now().toString());
        messagePayload.put("sender", buildSender(sender));
        Map<String, Object> envelope = Map.of(
                "type", "chat.message",
                "payload", messagePayload);
        broadcast(room.getId(), envelope);
    }

    public void publishPrivateChat(RoomSocketClient client, long targetPlayerId, String content) {
        if (!StringUtils.hasText(content) || targetPlayerId <= 0) {
            return;
        }
        Room room = resolveRoom(client.roomId).orElse(null);
        if (room == null) {
            return;
        }
        client.refreshPlayer(room, roomPlayerRepository);
        RoomPlayer sender = client.player;
        if (sender == null) {
            return;
        }
        RoomPlayer target = roomPlayerRepository.findById(targetPlayerId)
                .filter(player -> player.getRoom().getId().equals(room.getId()))
                .orElse(null);
        if (target == null) {
            return;
        }
        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", UUID.randomUUID().toString());
        messagePayload.put("roomId", room.getId());
        messagePayload.put("channel", "private");
        messagePayload.put("content", content);
        messagePayload.put("timestamp", Instant.now().toString());
        messagePayload.put("sender", buildDirectSender(sender));
        messagePayload.put("targetPlayerId", target.getId());
        Map<String, Object> envelope = Map.of(
                "type", "chat.direct",
                "payload", messagePayload);
        sendToPlayer(room, sender.getId(), envelope);
        sendToPlayer(room, target.getId(), envelope);
    }

    public void publishFactionChat(RoomSocketClient client, String content, String faction) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Room room = resolveRoom(client.roomId).orElse(null);
        if (room == null) {
            return;
        }
        client.refreshPlayer(room, roomPlayerRepository);
        RoomPlayer sender = client.player;
        if (sender == null) {
            return;
        }
        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", UUID.randomUUID().toString());
        messagePayload.put("roomId", room.getId());
        messagePayload.put("channel", "faction");
        messagePayload.put("content", content);
        messagePayload.put("timestamp", Instant.now().toString());
        messagePayload.put("sender", buildDirectSender(sender));
        if (StringUtils.hasText(faction)) {
            messagePayload.put("faction", faction);
        }
        Map<String, Object> envelope = Map.of(
                "type", "chat.direct",
                "payload", messagePayload);
        broadcast(room.getId(), envelope);
    }

    public void publishGameEvent(RoomSocketClient client, Map<String, Object> payload) {
        Room room = resolveRoom(client.roomId).orElse(null);
        if (room == null) {
            return;
        }
        Map<String, Object> messagePayload = new HashMap<>(payload != null ? payload : Map.of());
        messagePayload.put("timestamp", Instant.now().toString());
        Map<String, Object> envelope = Map.of(
                "type", "game.event",
                "payload", messagePayload);
        broadcast(room.getId(), envelope);
    }

    private void broadcast(long roomId, Map<String, Object> payload) {
        Set<RoomSocketClient> clients = activeRooms.getOrDefault(roomId, Collections.emptySet());
        for (RoomSocketClient client : clients) {
            transmit(client.session, payload);
        }
    }

    private void sendToPlayer(Room room, Long playerId, Map<String, Object> payload) {
        if (playerId == null) {
            return;
        }
        Set<RoomSocketClient> clients = activeRooms.getOrDefault(room.getId(), Collections.emptySet());
        for (RoomSocketClient client : clients) {
            client.refreshPlayer(room, roomPlayerRepository);
            RoomPlayer player = client.player;
            if (player != null && playerId.equals(player.getId())) {
                transmit(client.session, payload);
            }
        }
    }

    private Map<String, Object> resolveActor(RoomRealtimeEvents.Actor actor) {
        if (actor == null) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        if (actor.playerId() != null) {
            payload.put("id", actor.playerId());
        }
        if (StringUtils.hasText(actor.username())) {
            payload.put("username", actor.username());
        }
        if (StringUtils.hasText(actor.displayName())) {
            payload.put("display_name", actor.displayName());
        }
        if ((payload.get("username") == null || payload.get("display_name") == null) && actor.playerId() != null) {
            roomPlayerRepository.findById(actor.playerId()).ifPresent(player -> {
                payload.put("username", player.getUsername());
                payload.put("display_name", player.getDisplayName());
            });
        }
        return payload.isEmpty() ? null : payload;
    }

    private Map<String, Object> buildSender(RoomPlayer sender) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", sender.getId());
        payload.put("username", sender.getUsername());
        payload.put("display_name", sender.getDisplayName());
        return payload;
    }

    private Map<String, Object> buildDirectSender(RoomPlayer sender) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", sender.getId());
        payload.put("displayName", sender.getDisplayName());
        payload.put("display_name", sender.getDisplayName());
        return payload;
    }

    private void transmit(WebSocketSession session, Object payload) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException ex) {
            log.error("Failed to send websocket message to session {}", session.getId(), ex);
        }
    }

    static final class RoomSocketClient {
        private final WebSocketSession session;
        private final long roomId;
        private final UserAccount user;
        private volatile RoomPlayer player;

        private RoomSocketClient(WebSocketSession session, Room room, UserAccount user) {
            this.session = session;
            this.roomId = room.getId();
            this.user = user;
        }

        private void refreshPlayer(Room room, RoomPlayerRepository repository) {
            if (user == null) {
                this.player = null;
                return;
            }
            this.player = repository.findByRoomAndUser(room, user).orElse(null);
        }

        long getRoomId() {
            return roomId;
        }

        UserAccount getUser() {
            return user;
        }

        RoomPlayer getPlayer() {
            return player;
        }
    }
}
