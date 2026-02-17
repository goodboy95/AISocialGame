package com.aisocialgame.controller;

import com.aisocialgame.dto.BalanceView;
import com.aisocialgame.dto.CheckinResponse;
import com.aisocialgame.dto.CheckinStatusResponse;
import com.aisocialgame.dto.LedgerEntryView;
import com.aisocialgame.dto.PagedResponse;
import com.aisocialgame.dto.RedeemRequest;
import com.aisocialgame.dto.RedeemResponse;
import com.aisocialgame.dto.RedemptionView;
import com.aisocialgame.dto.UsageRecordView;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.model.User;
import com.aisocialgame.service.AuthService;
import com.aisocialgame.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final AuthService authService;
    private final WalletService walletService;

    public WalletController(AuthService authService, WalletService walletService) {
        this.authService = authService;
        this.walletService = walletService;
    }

    @PostMapping("/checkin")
    public ResponseEntity<CheckinResponse> checkin(@RequestHeader("X-Auth-Token") String token) {
        return ResponseEntity.ok(new CheckinResponse(walletService.checkin(requireUser(token))));
    }

    @GetMapping("/checkin-status")
    public ResponseEntity<CheckinStatusResponse> checkinStatus(@RequestHeader("X-Auth-Token") String token) {
        return ResponseEntity.ok(new CheckinStatusResponse(walletService.getCheckinStatus(requireUser(token))));
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceView> balance(@RequestHeader("X-Auth-Token") String token) {
        BalanceSnapshot snapshot = walletService.getBalance(requireUser(token));
        return ResponseEntity.ok(new BalanceView(snapshot));
    }

    @GetMapping("/usage-records")
    public ResponseEntity<PagedResponse<UsageRecordView>> usageRecords(@RequestHeader("X-Auth-Token") String token,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        var result = walletService.getUsageRecords(requireUser(token), page, size);
        List<UsageRecordView> items = result.items().stream().map(UsageRecordView::new).toList();
        return ResponseEntity.ok(new PagedResponse<>(items, result.page(), result.size(), result.total()));
    }

    @GetMapping("/ledger")
    public ResponseEntity<PagedResponse<LedgerEntryView>> ledger(@RequestHeader("X-Auth-Token") String token,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        var result = walletService.getLedgerEntries(requireUser(token), page, size);
        List<LedgerEntryView> items = result.items().stream().map(LedgerEntryView::new).toList();
        return ResponseEntity.ok(new PagedResponse<>(items, result.page(), result.size(), result.total()));
    }

    @PostMapping("/redeem")
    public ResponseEntity<RedeemResponse> redeem(@RequestHeader("X-Auth-Token") String token,
                                                 @Valid @RequestBody RedeemRequest request) {
        return ResponseEntity.ok(new RedeemResponse(walletService.redeemCode(requireUser(token), request.getCode())));
    }

    @GetMapping("/redemption-history")
    public ResponseEntity<PagedResponse<RedemptionView>> redemptionHistory(@RequestHeader("X-Auth-Token") String token,
                                                                           @RequestParam(defaultValue = "1") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        var result = walletService.getRedemptionHistory(requireUser(token), page, size);
        List<RedemptionView> items = result.items().stream().map(RedemptionView::new).toList();
        return ResponseEntity.ok(new PagedResponse<>(items, result.page(), result.size(), result.total()));
    }

    private User requireUser(String token) {
        User user = authService.authenticate(token);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user;
    }
}
