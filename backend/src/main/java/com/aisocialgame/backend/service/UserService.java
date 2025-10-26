package com.aisocialgame.backend.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aisocialgame.backend.dto.AuthDtos;
import com.aisocialgame.backend.entity.Room;
import com.aisocialgame.backend.entity.RoomPlayer;
import com.aisocialgame.backend.entity.UserAccount;
import com.aisocialgame.backend.repository.RefreshTokenRepository;
import com.aisocialgame.backend.repository.RoomPlayerRepository;
import com.aisocialgame.backend.repository.RoomRepository;
import com.aisocialgame.backend.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

    public UserService(
            UserRepository userRepository,
            RoomRepository roomRepository,
            RoomPlayerRepository roomPlayerRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount register(String username, String email, String password, String displayName) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("邮箱已被使用");
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName != null ? displayName : username);
        user.setAdmin(false);
        return userRepository.save(user);
    }

    public Optional<UserAccount> findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
    }

    public AuthDtos.UserProfile toProfile(UserAccount user) {
        return new AuthDtos.UserProfile(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatar(),
                user.getBio(),
                user.isAdmin());
    }

    @Transactional
    public void deleteAccount(UserAccount user) {
        roomRepository.findAll().stream()
                .filter(room -> room.getOwner() != null && room.getOwner().getId().equals(user.getId()))
                .forEach(room -> {
                    room.setOwner(null);
                    roomRepository.save(room);
                });
        refreshTokenRepository.deleteByUser(user);
        userRepository.delete(user);
    }

    public AuthDtos.UserExport exportUser(UserAccount user) {
        List<RoomPlayer> memberships = roomPlayerRepository.findAll().stream()
                .filter(player -> player.getUser() != null && player.getUser().getId().equals(user.getId()))
                .sorted(Comparator.comparing(RoomPlayer::getJoinedAt))
                .toList();

        List<AuthDtos.UserMembershipSnapshot> membershipDtos = memberships.stream()
                .map(player -> new AuthDtos.UserMembershipSnapshot(
                        player.getRoom().getId(),
                        player.getRoom().getName(),
                        player.getRoom().getCode(),
                        player.getRoom().getStatus().name().toLowerCase(),
                        formatter.format(player.getJoinedAt()),
                        player.isHost(),
                        player.isAi(),
                        player.getAiStyle(),
                        player.getRole(),
                        player.getWord(),
                        player.isAlive()))
                .collect(Collectors.toList());

        List<Room> ownedRooms = roomRepository.findAll().stream()
                .filter(room -> room.getOwner() != null && room.getOwner().getId().equals(user.getId()))
                .sorted(Comparator.comparing(Room::getCreatedAt))
                .toList();

        List<AuthDtos.UserOwnedRoomSnapshot> ownedRoomDtos = ownedRooms.stream()
                .map(room -> new AuthDtos.UserOwnedRoomSnapshot(
                        room.getId(),
                        room.getName(),
                        room.getCode(),
                        formatter.format(room.getCreatedAt()),
                        room.getStatus().name().toLowerCase()))
                .collect(Collectors.toList());

        AuthDtos.UserStatistics stats = new AuthDtos.UserStatistics(memberships.size(), ownedRooms.size());

        return new AuthDtos.UserExport(Instant.now(), toProfile(user), membershipDtos, ownedRoomDtos, stats);
    }
}
