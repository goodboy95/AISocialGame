package com.aisocialgame.websocket;

import com.aisocialgame.dto.ws.ChatMessage;
import com.aisocialgame.dto.ws.GameStateEvent;
import com.aisocialgame.dto.ws.PrivateEvent;
import com.aisocialgame.dto.ws.SeatEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GamePushService {
    private final SimpMessagingTemplate messagingTemplate;
    private final boolean enabled;

    public GamePushService(SimpMessagingTemplate messagingTemplate,
                           @Value("${app.websocket.enabled:true}") boolean enabled) {
        this.messagingTemplate = messagingTemplate;
        this.enabled = enabled;
    }

    public void pushStateChange(String roomId, GameStateEvent event) {
        if (!enabled || !StringUtils.hasText(roomId) || event == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", event);
    }

    public void pushPrivate(String playerId, PrivateEvent event) {
        if (!enabled || !StringUtils.hasText(playerId) || event == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(playerId, "/queue/private", event);
    }

    public void pushSeatChange(String roomId, SeatEvent event) {
        if (!enabled || !StringUtils.hasText(roomId) || event == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/seat", event);
    }

    public void pushChat(String roomId, ChatMessage message) {
        if (!enabled || !StringUtils.hasText(roomId) || message == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", message);
    }
}
