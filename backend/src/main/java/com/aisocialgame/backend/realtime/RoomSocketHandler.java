package com.aisocialgame.backend.realtime;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.entity.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aisocialgame.backend.service.RoomGameEventService;

@Component
public class RoomSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomSocketHandler.class);

    private final RoomSocketCoordinator coordinator;
    private final ObjectMapper objectMapper;
    private final RoomGameEventService gameEventService;

    public RoomSocketHandler(RoomSocketCoordinator coordinator, ObjectMapper objectMapper, RoomGameEventService gameEventService) {
        this.coordinator = coordinator;
        this.objectMapper = objectMapper;
        this.gameEventService = gameEventService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long roomId = extractRoomId(session.getUri());
        if (roomId == null) {
            log.warn("Closing websocket session {} because room id could not be determined", session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        Room room = coordinator.resolveRoom(roomId).orElse(null);
        if (room == null) {
            log.warn("Closing websocket session {} because room {} does not exist", session.getId(), roomId);
            session.close(new CloseStatus(4404, "room not found"));
            return;
        }
        UserAccount user = (UserAccount) session.getAttributes().get("authenticatedUser");
        if (user == null) {
            log.warn("Closing websocket session {} because authenticated user is missing", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        RoomSocketCoordinator.RoomSocketClient client = coordinator.register(room, user, session);
        coordinator.sendInitialSnapshot(client, room);
        RoomPlayer player = client.getPlayer();
        RoomRealtimeEvents.Actor actor = player != null
                ? new RoomRealtimeEvents.Actor(
                        player.getId(),
                        player.getUser() != null ? player.getUser().getId() : null,
                        player.getUsername(),
                        player.getDisplayName())
                : new RoomRealtimeEvents.Actor(null, user.getId(), user.getUsername(), user.getDisplayName());
        coordinator.broadcastRoomState(room, actor, "room.connection", null);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        RoomSocketCoordinator.RoomSocketClient client = coordinator.unregister(session);
        if (client == null) {
            return;
        }
        Room room = coordinator.resolveRoom(client.getRoomId()).orElse(null);
        if (room == null) {
            return;
        }
        RoomPlayer player = client.getPlayer();
        RoomRealtimeEvents.Actor actor = player != null
                ? new RoomRealtimeEvents.Actor(
                        player.getId(),
                        player.getUser() != null ? player.getUser().getId() : null,
                        player.getUsername(),
                        player.getDisplayName())
                : null;
        coordinator.broadcastRoomState(room, actor, "room.disconnection", null);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        RoomSocketCoordinator.RoomSocketClient client = coordinator.find(session);
        if (client == null) {
            log.warn("Received message for unknown session {}", session.getId());
            session.close(CloseStatus.PROTOCOL_ERROR);
            return;
        }
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        JsonNode payload = root.path("payload");
        if (!StringUtils.hasText(type)) {
            return;
        }
        switch (type) {
            case "chat.message" -> coordinator.publishPublicChat(client, payload.path("content").asText());
            case "chat.private" -> coordinator.publishPrivateChat(client, payload.path("targetPlayerId").asLong(),
                    payload.path("content").asText());
            case "chat.faction" -> coordinator.publishFactionChat(client, payload.path("content").asText(),
                    payload.path("faction").asText(null));
            case "game.event" -> {
                Map<String, Object> eventPayload = (payload != null && !payload.isMissingNode() && !payload.isNull())
                        ? objectMapper.convertValue(payload, Map.class)
                        : Map.of();
                String eventType = payload.path("event").asText();
                boolean handled = gameEventService.handleEvent(client.getRoomId(), client.getUser(), eventType, eventPayload);
                if (!handled) {
                    coordinator.publishGameEvent(client, eventPayload);
                }
            }
            default -> log.warn("Unsupported websocket message type '{}' from session {}", type, session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Websocket transport error on session {}", session.getId(), exception);
        session.close(CloseStatus.SERVER_ERROR);
    }

    private Long extractRoomId(URI uri) {
        if (uri == null) {
            return null;
        }
        String[] segments = uri.getPath().split("/");
        for (int i = 0; i < segments.length; i++) {
            if ("rooms".equals(segments[i]) && i + 1 < segments.length) {
                String candidate = segments[i + 1];
                try {
                    return Long.parseLong(candidate.replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {
                    log.debug("Failed to parse room id from path segment '{}'", candidate);
                }
            }
        }
        return null;
    }
}
