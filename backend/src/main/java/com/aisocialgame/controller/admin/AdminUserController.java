package com.aisocialgame.controller.admin;

import com.aisocialgame.dto.admin.AdminBanRequest;
import com.aisocialgame.dto.admin.AdminUnbanRequest;
import com.aisocialgame.dto.admin.AdminUserView;
import com.aisocialgame.service.AdminAuthService;
import com.aisocialgame.service.AdminOpsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminAuthService adminAuthService;
    private final AdminOpsService adminOpsService;

    public AdminUserController(AdminAuthService adminAuthService, AdminOpsService adminOpsService) {
        this.adminAuthService = adminAuthService;
        this.adminOpsService = adminOpsService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserView> getUser(@PathVariable long userId,
                                                 @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(adminOpsService.getUser(userId));
    }

    @PostMapping("/{userId}/ban")
    public ResponseEntity<AdminUserView> ban(@PathVariable long userId,
                                             @Valid @RequestBody AdminBanRequest request,
                                             @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(adminOpsService.banUser(userId, request.getReason(), request.isPermanent(), request.getExpiresAt()));
    }

    @PostMapping("/{userId}/unban")
    public ResponseEntity<AdminUserView> unban(@PathVariable long userId,
                                               @Valid @RequestBody AdminUnbanRequest request,
                                               @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(adminOpsService.unbanUser(userId, request.getReason()));
    }
}
