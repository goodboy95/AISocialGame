package com.aisocialgame.backend.websocket;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.repository.UserRepository;
import com.aisocialgame.backend.security.JwtService;

import io.jsonwebtoken.Claims;

@Component
public class JwtTokenHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenHandshakeInterceptor.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtTokenHandshakeInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("Rejecting WebSocket handshake due to unexpected request type: {}", request.getClass().getName());
            return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        if (!StringUtils.hasText(token)) {
            log.warn("Rejecting WebSocket handshake because access token is missing");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtService.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            UserAccount user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("Rejecting WebSocket handshake because user {} does not exist", userId);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put("user", user);
            attributes.put("tokenId", claims.getId());
            log.info("Accepted WebSocket handshake for user {} ({})", user.getId(), user.getUsername());
            return true;
        } catch (Exception ex) {
            log.warn("Rejecting WebSocket handshake due to invalid token", ex);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket handshake completed with exception", exception);
        }
    }
}
