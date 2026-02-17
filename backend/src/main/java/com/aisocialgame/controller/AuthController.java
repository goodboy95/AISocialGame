package com.aisocialgame.controller;

import com.aisocialgame.dto.AuthResponse;
import com.aisocialgame.dto.AuthUserView;
import com.aisocialgame.dto.LoginRequest;
import com.aisocialgame.dto.RegisterRequest;
import com.aisocialgame.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getNickname(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(
                request.getAccount(),
                request.getPassword(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserView> me(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        return ResponseEntity.ok(authService.currentUserView(token));
    }
}
