package com.aisocialgame.controller;

import com.aisocialgame.dto.ws.ChatMessage;
import com.aisocialgame.dto.ws.ChatMessageRequest;
import com.aisocialgame.model.GameState;
import com.aisocialgame.model.Room;
import com.aisocialgame.model.RoomSeat;
import com.aisocialgame.repository.GameStateRepository;
import com.aisocialgame.service.RoomService;
import com.aisocialgame.websocket.ChatRateLimiter;
import com.aisocialgame.websocket.GamePushService;
import com.aisocialgame.websocket.PlayerConnectionService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Controller
public class RoomChatController {
    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_EMOJI = "EMOJI";
    private static final String TYPE_QUICK_PHRASE = "QUICK_PHRASE";

    private final RoomService roomService;
    private final GameStateRepository gameStateRepository;
    private final ChatRateLimiter chatRateLimiter;
    private final GamePushService gamePushService;
    private final PlayerConnectionService playerConnectionService;

    public RoomChatController(RoomService roomService,
                              GameStateRepository gameStateRepository,
                              ChatRateLimiter chatRateLimiter,
                              GamePushService gamePushService,
                              PlayerConnectionService playerConnectionService) {
        this.roomService = roomService;
        this.gameStateRepository = gameStateRepository;
        this.chatRateLimiter = chatRateLimiter;
        this.gamePushService = gamePushService;
        this.playerConnectionService = playerConnectionService;
    }

    @MessageMapping("/room/{roomId}/chat")
    public void handleChat(@DestinationVariable String roomId,
                           @Payload ChatMessageRequest request,
                           Principal principal) {
        if (principal == null || !StringUtils.hasText(principal.getName()) || request == null) {
            return;
        }

        Room room = roomService.getRoom(roomId);
        Optional<RoomSeat> maybeSeat = room.getSeats().stream()
                .filter(seat -> principal.getName().equals(seat.getPlayerId()))
                .findFirst();
        if (maybeSeat.isEmpty()) {
            return;
        }

        if (!chatRateLimiter.allowMessage(principal.getName())) {
            return;
        }

        String type = normalizeType(request.getType());
        if (!isChatAllowed(roomId, type)) {
            return;
        }

        String content = sanitizeContent(type, request.getContent());
        if (!StringUtils.hasText(content)) {
            return;
        }

        RoomSeat seat = maybeSeat.get();
        ChatMessage message = new ChatMessage(
                UUID.randomUUID().toString(),
                roomId,
                seat.getPlayerId(),
                seat.getDisplayName(),
                seat.getAvatar(),
                type,
                content,
                System.currentTimeMillis()
        );
        gamePushService.pushChat(roomId, message);
        playerConnectionService.markActive(seat.getPlayerId(), roomId);
    }

    private String normalizeType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return TYPE_TEXT;
        }
        String value = rawType.trim().toUpperCase(Locale.ROOT);
        if (TYPE_EMOJI.equals(value) || TYPE_QUICK_PHRASE.equals(value)) {
            return value;
        }
        return TYPE_TEXT;
    }

    private boolean isChatAllowed(String roomId, String type) {
        if (!TYPE_TEXT.equals(type)) {
            return true;
        }
        GameState state = gameStateRepository.findById(roomId).orElse(null);
        if (state == null) {
            return true;
        }
        return !"NIGHT".equals(state.getPhase());
    }

    private String sanitizeContent(String type, String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim();
        int maxLength = TYPE_TEXT.equals(type) ? 200 : 40;
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        return normalized;
    }
}
