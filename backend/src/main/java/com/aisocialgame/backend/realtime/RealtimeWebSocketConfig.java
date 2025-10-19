package com.aisocialgame.backend.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

    private final RoomSocketHandler roomSocketHandler;
    private final WebSocketAccessTokenInterceptor accessTokenInterceptor;

    public RealtimeWebSocketConfig(RoomSocketHandler roomSocketHandler,
            WebSocketAccessTokenInterceptor accessTokenInterceptor) {
        this.roomSocketHandler = roomSocketHandler;
        this.accessTokenInterceptor = accessTokenInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomSocketHandler, "/ws/rooms/{roomId}", "/api/ws/rooms/{roomId}")
                .addInterceptors(accessTokenInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
