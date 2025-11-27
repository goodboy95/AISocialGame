package com.aisocialgame.service.token;

public interface TokenStore {
    void store(String token, String userId);

    String getUserId(String token);

    void revoke(String token);
}
