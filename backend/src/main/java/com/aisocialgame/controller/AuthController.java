package com.aisocialgame.controller;

import com.aisocialgame.dto.AuthResponse;
import com.aisocialgame.dto.AuthUserView;
import com.aisocialgame.dto.SsoCallbackRequest;
import com.aisocialgame.dto.SsoUrlResponse;
import com.aisocialgame.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/sso-url")
    public ResponseEntity<SsoUrlResponse> getSsoUrl() {
        return ResponseEntity.ok(authService.getSsoUrl());
    }

    @PostMapping("/sso-callback")
    public ResponseEntity<AuthResponse> ssoCallback(@Valid @RequestBody SsoCallbackRequest request) {
        AuthResponse response = authService.ssoCallback(
                request.getUserId(),
                request.getUsername(),
                request.getSessionId(),
                request.getAccessToken()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserView> me(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        return ResponseEntity.ok(authService.currentUserView(token));
    }
}
