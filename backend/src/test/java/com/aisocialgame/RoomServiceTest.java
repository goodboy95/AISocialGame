package com.aisocialgame;

import com.aisocialgame.model.Room;
import com.aisocialgame.model.User;
import com.aisocialgame.repository.UserRepository;
import com.aisocialgame.service.RoomService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

@SpringBootTest(classes = AiSocialGameApplication.class)
@ActiveProfiles("test")
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createRoomAndJoin() {
        var user = createLocalUser("test@example.com", "测试用户");
        Room room = roomService.createRoom("undercover", "测试房间", false, null, "voice", Map.of("playerCount", 6), user);
        Assertions.assertNotNull(room.getId());
        Assertions.assertEquals(1, room.getSeats().size());

        roomService.joinRoom(room.getId(), "访客A", null, null);
        Assertions.assertEquals(2, roomService.getRoom(room.getId()).getSeats().size());
    }

    private User createLocalUser(String email, String nickname) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setUsername(email.substring(0, email.indexOf("@")));
        user.setPassword("{test}");
        user.setNickname(nickname);
        user.setAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + nickname);
        user.setLevel(1);
        user.setCoins(0);
        return userRepository.save(user);
    }
}
