package com.aisocialgame.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aisocialgame.backend.dto.AuthDtos;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.service.AuthService;
import com.aisocialgame.backend.service.UserService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register/")
    public ResponseEntity<?> register(@RequestBody AuthDtos.RegisterRequest request) {
        UserAccount user = userService.register(request.username(), request.email(), request.password(), request.displayName());
        return ResponseEntity.ok(userService.toProfile(user));
    }

    @PostMapping("/token/")
    public AuthDtos.TokenResponse login(@RequestBody AuthDtos.TokenRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/token/refresh/")
    public AuthDtos.TokenResponse refresh(@RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request.refresh());
    }

    @PostMapping("/logout/")
    public ResponseEntity<Void> logout(@RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request.refresh());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/")
    public ResponseEntity<AuthDtos.UserProfile> me() {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.toProfile(current));
    }

    @GetMapping("/me/export/")
    public ResponseEntity<AuthDtos.UserExport> export() {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.exportUser(current));
    }

    @DeleteMapping("/me/")
    public ResponseEntity<Void> deleteAccount() {
        UserAccount current = authService.currentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        userService.deleteAccount(current);
        return ResponseEntity.noContent().build();
    }
}
