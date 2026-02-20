package com.aisocialgame.controller.admin;

import com.aisocialgame.dto.BalanceView;
import com.aisocialgame.dto.admin.AdminAdjustBalanceRequest;
import com.aisocialgame.dto.admin.AdminLedgerPageResponse;
import com.aisocialgame.dto.admin.AdminMigrateBalanceRequest;
import com.aisocialgame.dto.admin.AdminReverseBalanceRequest;
import com.aisocialgame.service.AdminAuthService;
import com.aisocialgame.service.AdminOpsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/adjust")
    public ResponseEntity<BalanceView> adjust(@Valid @RequestBody AdminAdjustBalanceRequest request,
                                              @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        String operator = adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(new BalanceView(
                adminOpsService.adjustBalance(request.getUserId(), request.getDeltaTemp(), request.getDeltaPermanent(), request.getReason(), operator, request.getRequestId())
        ));
    }

    @PostMapping("/reversal")
    public ResponseEntity<BalanceView> reversal(@Valid @RequestBody AdminReverseBalanceRequest request,
                                                @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        String operator = adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(new BalanceView(
                adminOpsService.reverseBalance(request.getUserId(), request.getOriginalRequestId(), request.getReason(), operator)
        ));
    }

    @PostMapping("/migrate-user")
    public ResponseEntity<BalanceView> migrateUser(@Valid @RequestBody AdminMigrateBalanceRequest request,
                                                   @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        String operator = adminAuthService.requireAdmin(token);
        return ResponseEntity.ok(new BalanceView(adminOpsService.migrateUserBalance(request.getUserId(), operator)));
    }
}
