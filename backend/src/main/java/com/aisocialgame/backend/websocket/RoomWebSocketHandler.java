package com.aisocialgame.backend.websocket;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.aisocialgame.backend.dto.RoomDtos;
import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.repository.RoomPlayerRepository;
import com.aisocialgame.backend.service.RoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);
    private static final Pattern ROOM_PATH_PATTERN = Pattern.compile(".*?/ws/rooms/([0-9]+)/?");

    private final RoomService roomService;
    private final RoomPlayerRepository roomPlayerRepository;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<ClientConnection>> rooms = new ConcurrentHashMap<>();

    public RoomWebSocketHandler(RoomService roomService, RoomPlayerRepository roomPlayerRepository, ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.roomPlayerRepository = roomPlayerRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long roomId = resolveRoomId(session.getUri());
        if (roomId == null) {
            log.warn("Closing session {} because room id could not be resolved", session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        Optional<Room> optionalRoom = roomService.findById(roomId);
        if (optionalRoom.isEmpty()) {
            log.warn("Closing session {} because room {} does not exist", session.getId(), roomId);
            session.close(new CloseStatus(4404, "room not found"));
            return;
        }
        UserAccount user = (UserAccount) session.getAttributes().get("user");
        if (user == null) {
            log.warn("Closing session {} because authenticated user is missing", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        Room room = optionalRoom.get();
        ClientConnection connection = new ClientConnection(session, roomId, user);
        connection.refreshPlayer(room, roomPlayerRepository);
        rooms.computeIfAbsent(roomId, key -> new CopyOnWriteArraySet<>()).add(connection);
        log.info("WebSocket session {} connected to room {} as user {}", session.getId(), roomId, user.getUsername());
        sendSystemSync(connection, room);
        RoomEvents.ActorSnapshot actorSnapshot = connection.player != null
                ? new RoomEvents.ActorSnapshot(connection.player.getId(),
                        connection.player.getUser() != null ? connection.player.getUser().getId() : null,
                        connection.player.getUsername(),
                        connection.player.getDisplayName())
                : null;
        broadcastRoomUpdate(roomId, actorSnapshot, "room.connection", null);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ClientConnection connection = findConnection(session);
        if (connection == null) {
            log.warn("Received message for unknown session {}", session.getId());
            session.close(CloseStatus.PROTOCOL_ERROR);
            return;
        }
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        JsonNode payload = root.path("payload");
        if (!StringUtils.hasText(type)) {
            log.warn("Ignoring message without type from session {}", session.getId());
            return;
        }
        switch (type) {
            case "chat.message" -> handlePublicChat(connection, payload);
            case "chat.private" -> handlePrivateChat(connection, payload);
            case "chat.faction" -> handleFactionChat(connection, payload);
            case "game.event" -> handleGameEvent(connection, payload);
            default -> log.warn("Unsupported message type '{}' from session {}", type, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ClientConnection connection = removeConnection(session);
        if (connection != null) {
            log.info("WebSocket session {} disconnected from room {} with status {}", session.getId(), connection.roomId, status);
            RoomEvents.ActorSnapshot actorSnapshot = connection.player != null
                    ? new RoomEvents.ActorSnapshot(connection.player.getId(),
                            connection.player.getUser() != null ? connection.player.getUser().getId() : null,
                            connection.player.getUsername(),
                            connection.player.getDisplayName())
                    : null;
            broadcastRoomUpdate(connection.roomId, actorSnapshot, "room.disconnection", null);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error on session {}", session.getId(), exception);
        session.close(CloseStatus.SERVER_ERROR);
    }

    public void broadcastRoomUpdate(long roomId, RoomEvents.ActorSnapshot actorSnapshot, String event, String message) {
        Set<ClientConnection> connections = rooms.getOrDefault(roomId, Collections.emptySet());
        if (connections.isEmpty()) {
            return;
        }
        Optional<Room> roomOptional = roomService.findById(roomId);
        if (roomOptional.isEmpty()) {
            return;
        }
        Room room = roomOptional.get();
        Map<String, Object> actorPayload = null;
        if (actorSnapshot != null) {
            actorPayload = new HashMap<>();
            actorPayload.put("id", actorSnapshot.playerId());
            if (StringUtils.hasText(actorSnapshot.username())) {
                actorPayload.put("username", actorSnapshot.username());
            }
            if (StringUtils.hasText(actorSnapshot.displayName())) {
                actorPayload.put("display_name", actorSnapshot.displayName());
            }
            if ((actorPayload.get("username") == null || actorPayload.get("display_name") == null)
                    && actorSnapshot.playerId() != null) {
                RoomPlayer actorEntity = roomPlayerRepository.findById(actorSnapshot.playerId()).orElse(null);
                if (actorEntity != null) {
                    actorPayload = buildActorPayload(actorEntity);
                }
            }
        }
        Instant now = Instant.now();
        for (ClientConnection connection : connections) {
            connection.refreshPlayer(room, roomPlayerRepository);
            RoomDtos.RoomDetail detail = roomService.toRoomDetail(room, connection.user);
            Map<String, Object> payload = new HashMap<>();
            payload.put("room", detail);
            if (actorPayload != null) {
                payload.put("actor", actorPayload);
            }
            if (StringUtils.hasText(message)) {
                payload.put("message", message);
            }
            if (StringUtils.hasText(event)) {
                payload.put("event", event);
            }
            payload.put("timestamp", now.toString());
            sendMessage(connection.session, Map.of("type", "system.broadcast", "payload", payload));
        }
    }

    public void closeRoom(long roomId, String reason) {
        Set<ClientConnection> connections = rooms.remove(roomId);
        if (connections == null || connections.isEmpty()) {
            return;
        }
        for (ClientConnection connection : connections) {
            try {
                connection.session.close(new CloseStatus(4000, reason != null ? reason : "room removed"));
            } catch (IOException e) {
                log.warn("Failed to close session {} for removed room {}", connection.session.getId(), roomId, e);
            }
        }
    }

    private void handlePublicChat(ClientConnection connection, JsonNode payload) {
        String content = payload.path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            return;
        }
        RoomPlayer sender = ensurePlayer(connection);
        if (sender == null) {
            return;
        }
        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", UUID.randomUUID().toString());
        messagePayload.put("content", content);
        messagePayload.put("timestamp", Instant.now().toString());
        messagePayload.put("sender", buildSenderPayload(sender));
        broadcastToRoom(connection.roomId, Map.of("type", "chat.message", "payload", messagePayload));
    }

    private void handlePrivateChat(ClientConnection connection, JsonNode payload) {
        String content = payload.path("content").asText(null);
        long targetPlayerId = payload.path("targetPlayerId").asLong(-1);
        if (!StringUtils.hasText(content) || targetPlayerId <= 0) {
            return;
        }
        RoomPlayer sender = ensurePlayer(connection);
        if (sender == null) {
            return;
        }
        Optional<RoomPlayer> targetOpt = roomPlayerRepository.findById(targetPlayerId)
                .filter(player -> player.getRoom().getId().equals(connection.roomId));
        if (targetOpt.isEmpty()) {
            log.warn("Failed to deliver private message: target {} not found in room {}", targetPlayerId, connection.roomId);
            return;
        }
        RoomPlayer target = targetOpt.get();
        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", UUID.randomUUID().toString());
        messagePayload.put("roomId", connection.roomId);
        messagePayload.put("channel", "private");
        messagePayload.put("content", content);
        messagePayload.put("timestamp", Instant.now().toString());
        messagePayload.put("sender", buildDirectSenderPayload(sender));
        messagePayload.put("targetPlayerId", target.getId());
        sendToPlayer(connection.roomId, sender.getId(), Map.of("type", "chat.direct", "payload", messagePayload));
        sendToPlayer(connection.roomId, target.getId(), Map.of("type", "chat.direct", "payload", messagePayload));
    }

    private void handleFactionChat(ClientConnection connection, JsonNode payload) {
        String content = payload.path("content").asText(null);
        String faction = payload.path("faction").asText(null);
        if (!StringUtils.hasText(content)) {
            return;
        }
        RoomPlayer sender = ensurePlayer(connection);
        if (sender == null) {
            return;
        }
        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", UUID.randomUUID().toString());
        messagePayload.put("roomId", connection.roomId);
        messagePayload.put("channel", "faction");
        messagePayload.put("content", content);
        messagePayload.put("timestamp", Instant.now().toString());
        messagePayload.put("sender", buildDirectSenderPayload(sender));
        if (StringUtils.hasText(faction)) {
            messagePayload.put("faction", faction);
        }
        broadcastToRoom(connection.roomId, Map.of("type", "chat.direct", "payload", messagePayload));
    }

    private void handleGameEvent(ClientConnection connection, JsonNode payload) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("type", "game.event");
        Map<String, Object> eventPayload = new HashMap<>();
        if (payload != null && !payload.isMissingNode() && !payload.isNull()) {
            eventPayload.putAll(objectMapper.convertValue(payload, Map.class));
        }
        eventPayload.put("timestamp", Instant.now().toString());
        envelope.put("payload", eventPayload);
        broadcastToRoom(connection.roomId, envelope);
    }

    private void broadcastToRoom(long roomId, Map<String, Object> payload) {
        Set<ClientConnection> connections = rooms.getOrDefault(roomId, Collections.emptySet());
        for (ClientConnection connection : connections) {
            sendMessage(connection.session, payload);
        }
    }

    private void sendToPlayer(long roomId, Long playerId, Map<String, Object> payload) {
        if (playerId == null) {
            return;
        }
        Set<ClientConnection> connections = rooms.getOrDefault(roomId, Collections.emptySet());
        for (ClientConnection connection : connections) {
            if (connection.player != null && playerId.equals(connection.player.getId())) {
                sendMessage(connection.session, payload);
            }
        }
    }

    private void sendSystemSync(ClientConnection connection, Room room) {
        RoomDtos.RoomDetail detail = roomService.toRoomDetail(room, connection.user);
        sendMessage(connection.session, Map.of("type", "system.sync", "payload", detail));
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> payload) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            log.error("Failed to send message to session {}", session.getId(), e);
        }
    }

    private ClientConnection removeConnection(WebSocketSession session) {
        for (Map.Entry<Long, Set<ClientConnection>> entry : rooms.entrySet()) {
            Set<ClientConnection> connections = entry.getValue();
            for (ClientConnection connection : connections) {
                if (connection.session.equals(session)) {
                    connections.remove(connection);
                    if (connections.isEmpty()) {
                        rooms.remove(entry.getKey(), connections);
                    }
                    return connection;
                }
            }
        }
        return null;
    }

    private ClientConnection findConnection(WebSocketSession session) {
        for (Set<ClientConnection> connections : rooms.values()) {
            for (ClientConnection connection : connections) {
                if (connection.session.equals(session)) {
                    return connection;
                }
            }
        }
        return null;
    }

    private RoomPlayer ensurePlayer(ClientConnection connection) {
        if (connection.player == null) {
            Room room = roomService.findById(connection.roomId).orElse(null);
            if (room == null) {
                return null;
            }
            connection.refreshPlayer(room, roomPlayerRepository);
        }
        if (connection.player == null) {
            log.warn("User {} attempted action without joining room {}", connection.user.getUsername(), connection.roomId);
        }
        return connection.player;
    }

    private Map<String, Object> buildSenderPayload(RoomPlayer sender) {
        Map<String, Object> senderPayload = new HashMap<>();
        senderPayload.put("id", sender.getId());
        senderPayload.put("username", sender.getUsername());
        senderPayload.put("display_name", sender.getDisplayName());
        return senderPayload;
    }

    private Map<String, Object> buildDirectSenderPayload(RoomPlayer sender) {
        Map<String, Object> senderPayload = new HashMap<>();
        senderPayload.put("id", sender.getId());
        senderPayload.put("displayName", sender.getDisplayName());
        senderPayload.put("display_name", sender.getDisplayName());
        return senderPayload;
    }

    private Map<String, Object> buildActorPayload(RoomPlayer actor) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", actor.getId());
        payload.put("username", actor.getUsername());
        payload.put("display_name", actor.getDisplayName());
        return payload;
    }

    private Long resolveRoomId(URI uri) {
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        Matcher matcher = ROOM_PATH_PATTERN.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    private static final class ClientConnection {
        private final WebSocketSession session;
        private final long roomId;
        private final UserAccount user;
        private volatile RoomPlayer player;

        private ClientConnection(WebSocketSession session, long roomId, UserAccount user) {
            this.session = session;
            this.roomId = roomId;
            this.user = user;
        }

        private void refreshPlayer(Room room, RoomPlayerRepository repository) {
            if (user == null) {
                this.player = null;
                return;
            }
            this.player = repository.findByRoomAndUser(room, user).orElse(null);
        }
    }
}
