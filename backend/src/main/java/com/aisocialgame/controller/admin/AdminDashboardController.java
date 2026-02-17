package com.aisocialgame.controller.admin;

import com.aisocialgame.dto.admin.AdminDashboardSummaryResponse;
import com.aisocialgame.service.AdminAuthService;
import com.aisocialgame.service.AdminOpsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminAuthService adminAuthService;
    private final AdminOpsService adminOpsService;

    public AdminDashboardController(AdminAuthService adminAuthService, AdminOpsService adminOpsService) {
        this.adminAuthService = adminAuthService;
        this.adminOpsService = adminOpsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminDashboardSummaryResponse> summary(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(adminOpsService.dashboardSummary());
    }
}
