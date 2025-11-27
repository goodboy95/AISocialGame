package com.aisocialgame.controller;

import com.aisocialgame.dto.CommunityPostRequest;
import com.aisocialgame.model.CommunityPost;
import com.aisocialgame.model.User;
import com.aisocialgame.service.AuthService;
import com.aisocialgame.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService communityService;
    private final AuthService authService;

    public CommunityController(CommunityService communityService, AuthService authService) {
        this.communityService = communityService;
        this.authService = authService;
    }

    @GetMapping("/posts")
    public ResponseEntity<List<CommunityPost>> list() {
        return ResponseEntity.ok(communityService.list());
    }

    @PostMapping("/posts")
    public ResponseEntity<CommunityPost> create(@Valid @RequestBody CommunityPostRequest request,
                                                @RequestHeader(value = "X-Auth-Token", required = false) String token,
                                                @RequestHeader(value = "X-Guest-Name", required = false) String guestName) {
        User user = authService.authenticate(token);
        CommunityPost post = communityService.create(request, user, guestName);
        return ResponseEntity.ok(post);
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<CommunityPost> like(@PathVariable String id) {
        return ResponseEntity.ok(communityService.like(id));
    }
}
