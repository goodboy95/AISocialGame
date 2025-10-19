package com.aisocialgame.backend.realtime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.repository.UserRepository;
import com.aisocialgame.backend.security.JwtService;

import io.jsonwebtoken.Claims;

@Component
public class WebSocketAccessTokenInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAccessTokenInterceptor.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public WebSocketAccessTokenInterceptor(JwtService jwtService, UserRepository userRepository) {
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
            log.warn("Rejected websocket handshake due to unsupported request type: {}", request.getClass().getName());
            return false;
        }
        Map<String, String[]> parameters = servletRequest.getServletRequest().getParameterMap();
        String[] tokenValues = parameters.get("token");
        String token = tokenValues != null && tokenValues.length > 0 ? tokenValues[0] : null;
        if (!StringUtils.hasText(token)) {
            log.warn("Rejected websocket handshake because access token was missing");
            if (response != null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
            }
            return false;
        }
        try {
            Claims claims = jwtService.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            UserAccount user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("Rejected websocket handshake because user {} does not exist", userId);
                if (response != null) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                }
                return false;
            }
            attributes.put("authenticatedUser", user);
            attributes.put("accessTokenId", claims.getId());
            log.info("Accepted websocket handshake for user {} ({})", user.getId(), user.getUsername());
            return true;
        } catch (Exception exception) {
            log.warn("Rejected websocket handshake due to invalid access token", exception);
            if (response != null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
            }
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception ex) {
        if (ex != null) {
            log.error("Websocket handshake completed with exception", ex);
        }
    }
}
