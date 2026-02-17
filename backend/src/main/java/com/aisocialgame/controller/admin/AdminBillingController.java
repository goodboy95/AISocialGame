package com.aisocialgame.controller.admin;

import com.aisocialgame.dto.BalanceView;
import com.aisocialgame.dto.admin.AdminLedgerPageResponse;
import com.aisocialgame.service.AdminAuthService;
import com.aisocialgame.service.AdminOpsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/billing")
public class AdminBillingController {
    private final AdminAuthService adminAuthService;
    private final AdminOpsService adminOpsService;

    public AdminBillingController(AdminAuthService adminAuthService, AdminOpsService adminOpsService) {
        this.adminAuthService = adminAuthService;
        this.adminOpsService = adminOpsService;
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceView> balance(@RequestParam long userId,
                                               @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(new BalanceView(adminOpsService.getBalance(userId)));
    }

    @GetMapping("/ledger")
    public ResponseEntity<AdminLedgerPageResponse> ledger(@RequestParam long userId,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size,
                                                          @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(adminOpsService.getLedger(userId, page, size));
    }
}
