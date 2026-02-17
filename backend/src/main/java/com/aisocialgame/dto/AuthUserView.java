package com.aisocialgame.dto;

import com.aisocialgame.integration.grpc.dto.BalanceSnapshot;
import com.aisocialgame.model.User;

public class AuthUserView {
    private String id;
    private Long externalUserId;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private int level;
    private long coins;
    private BalanceView balance;

    public AuthUserView(User user, BalanceSnapshot balanceSnapshot) {
        this.id = user.getId();
        this.externalUserId = user.getExternalUserId();
        this.username = user.getUsername();
        this.nickname = user.getNickname();
        this.email = user.getEmail();
        this.avatar = user.getAvatar();
        this.level = user.getLevel();
        this.coins = balanceSnapshot.totalTokens();
        this.balance = new BalanceView(balanceSnapshot);
    }

    public String getId() {
        return id;
    }

    public Long getExternalUserId() {
        return externalUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatar() {
        return avatar;
    }

    public int getLevel() {
        return level;
    }

    public long getCoins() {
        return coins;
    }

    public BalanceView getBalance() {
        return balance;
    }
}
