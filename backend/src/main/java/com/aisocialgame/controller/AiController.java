package com.aisocialgame.controller;

import com.aisocialgame.dto.AiChatRequest;
import com.aisocialgame.dto.AiChatResponse;
import com.aisocialgame.dto.AiModelView;
import com.aisocialgame.model.User;
import com.aisocialgame.service.AiProxyService;
import com.aisocialgame.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiProxyService aiProxyService;
    private final AuthService authService;

    public AiController(AiProxyService aiProxyService, AuthService authService) {
        this.aiProxyService = aiProxyService;
        this.authService = authService;
    }

    @GetMapping("/models")
    public ResponseEntity<List<AiModelView>> listModels() {
        return ResponseEntity.ok(aiProxyService.listModels().stream().map(AiModelView::new).toList());
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request,
                                               @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        User user = authService.authenticate(token);
        return ResponseEntity.ok(new AiChatResponse(aiProxyService.chat(request, user)));
    }
}
