package com.aisocialgame;

import com.aisocialgame.model.Room;
import com.aisocialgame.service.AuthService;
import com.aisocialgame.service.RoomService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

@SpringBootTest(classes = AiSocialGameApplication.class)
@ActiveProfiles("test")
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private AuthService authService;

    @Test
    void createRoomAndJoin() {
        var user = authService.register("test@example.com", "password", "测试用户");
        Room room = roomService.createRoom("undercover", "测试房间", false, null, "voice", Map.of("playerCount", 6), user);
        Assertions.assertNotNull(room.getId());
        Assertions.assertEquals(1, room.getSeats().size());

        roomService.joinRoom(room.getId(), "访客A", null, null);
        Assertions.assertEquals(2, roomService.getRoom(room.getId()).getSeats().size());
    }
}
