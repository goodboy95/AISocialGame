package com.aisocialgame.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {
    private final PlayerConnectionService playerConnectionService;

    public WebSocketEventListener(PlayerConnectionService playerConnectionService) {
        this.playerConnectionService = playerConnectionService;
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getUser() == null) {
            return;
        }
        playerConnectionService.onConnect(
                accessor.getUser().getName(),
                accessor.getFirstNativeHeader("X-Room-Id"),
                accessor.getSessionId()
        );
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        playerConnectionService.onDisconnect(event.getSessionId());
    }
}
