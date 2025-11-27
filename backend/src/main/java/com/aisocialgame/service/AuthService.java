package com.aisocialgame.service;

import com.aisocialgame.exception.ApiException;
import com.aisocialgame.model.User;
import com.aisocialgame.repository.UserRepository;
import com.aisocialgame.service.token.TokenStore;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final TokenStore tokenStore;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, TokenStore tokenStore) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
    }

    public User register(String email, String password, String nickname) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邮箱已被注册");
        }
        String userId = UUID.randomUUID().toString();
        String avatar = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + nickname.replace(" ", "");
        User user = new User(userId, normalizedEmail, encoder.encode(password), nickname.trim(), avatar, 1000, 1);
        userRepository.save(user);
        return user;
    }

    public String issueToken(User user) {
        String token = UUID.randomUUID().toString();
        tokenStore.store(token, user.getId());
        return token;
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        if (!encoder.matches(password, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "密码错误");
        }
        return user;
    }

    public User authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        String userId = tokenStore.getUserId(token);
        if (userId == null) return null;
        return userRepository.findById(userId).orElse(null);
    }
}
