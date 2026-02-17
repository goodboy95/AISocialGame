package com.aisocialgame.dto.admin;

import com.aisocialgame.dto.BalanceView;
import com.aisocialgame.integration.grpc.dto.BanStatusSnapshot;
import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.integration.grpc.dto.ExternalUserProfile;

import java.time.Instant;

public class AdminUserView {
    private long userId;
    private String username;
    private String email;
    private String avatarUrl;
    private boolean active;
    private Instant bannedUntil;
    private Instant createdAt;
    private BanStatus banStatus;
    private BalanceView balance;

    public AdminUserView(ExternalUserProfile user, BanStatusSnapshot banStatusSnapshot, BalanceSnapshot balanceSnapshot) {
        this.userId = user.userId();
        this.username = user.username();
        this.email = user.email();
        this.avatarUrl = user.avatarUrl();
        this.active = user.active();
        this.bannedUntil = user.bannedUntil();
        this.createdAt = user.createdAt();
        this.banStatus = new BanStatus(
                banStatusSnapshot.banned(),
                banStatusSnapshot.banType(),
                banStatusSnapshot.reason(),
                banStatusSnapshot.expiresAt(),
                banStatusSnapshot.bannedAt()
        );
        this.balance = new BalanceView(balanceSnapshot);
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getBannedUntil() {
        return bannedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BanStatus getBanStatus() {
        return banStatus;
    }

    public BalanceView getBalance() {
        return balance;
    }

    public record BanStatus(
            boolean banned,
            String banType,
            String reason,
            Instant expiresAt,
            Instant bannedAt
    ) {
    }
}
