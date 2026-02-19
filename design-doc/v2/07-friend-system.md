# 模块 07：好友系统

> 优先级：P1 | 阶段：第二阶段 | 依赖：模块 01（WebSocket） | 被依赖：无

## 1. 背景与目标

当前平台没有好友关系。玩家之间的社交仅限于同一房间内的游戏互动，游戏结束后无法保持联系。社区模块也没有关注/粉丝机制。

目标：建立好友关系链，支持在线状态感知、游戏邀请、好友对局历史，增强社交粘性和留存。

## 2. 功能范围

| 功能 | 说明 |
|------|------|
| 添加好友 | 通过用户名/ID 搜索并发送好友请求 |
| 好友请求管理 | 接受/拒绝好友请求，查看待处理列表 |
| 好友列表 | 查看所有好友，显示在线状态和当前活动 |
| 在线状态 | 实时显示好友是否在线、正在哪个房间游戏 |
| 游戏邀请 | 向好友发送房间邀请 |
| 好友对局历史 | 查看与某个好友的共同对局记录 |
| 删除好友 | 双向解除好友关系 |

## 3. 数据模型

### 3.1 好友关系表

```sql
CREATE TABLE friendships (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL,       -- 发起方
    friend_id   VARCHAR(36) NOT NULL,       -- 接收方
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | ACCEPTED | REJECTED
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (user_id, friend_id),
    INDEX idx_friend (friend_id, status),
    INDEX idx_user (user_id, status)
);
```

### 3.2 JPA 实体

```java
@Entity
@Table(name = "friendships")
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String friendId;

    @Enumerated(EnumType.STRING)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

public enum FriendshipStatus {
    PENDING, ACCEPTED, REJECTED
}
```

### 3.3 Repository

```java
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findByUserIdAndFriendId(String userId, String friendId);

    // 查询某用户的所有已接受好友
    @Query("SELECT f FROM Friendship f WHERE (f.userId = :uid OR f.friendId = :uid) AND f.status = 'ACCEPTED'")
    List<Friendship> findAcceptedFriends(@Param("uid") String userId);

    // 查询待处理的好友请求（别人发给我的）
    List<Friendship> findByFriendIdAndStatus(String friendId, FriendshipStatus status);

    // 查询我发出的待处理请求
    List<Friendship> findByUserIdAndStatus(String userId, FriendshipStatus status);

    // 检查两人是否已是好友
    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE " +
           "((f.userId = :a AND f.friendId = :b) OR (f.userId = :b AND f.friendId = :a)) " +
           "AND f.status = 'ACCEPTED'")
    boolean areFriends(@Param("a") String a, @Param("b") String b);
}
```

## 4. 后端 API

### 4.1 Controller

```java
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    // 搜索用户（用于添加好友）
    @GetMapping("/search")
    public List<UserBrief> searchUsers(@RequestParam String keyword,
            @AuthenticationPrincipal User user) {
        return friendService.searchUsers(keyword, user.getId());
    }

    // 发送好友请求
    @PostMapping("/request")
    public ResponseEntity<Void> sendRequest(@RequestBody FriendRequest request,
            @AuthenticationPrincipal User user) {
        friendService.sendRequest(user.getId(), request.targetUserId());
        return ResponseEntity.ok().build();
    }

    // 处理好友请求
    @PostMapping("/request/{requestId}/respond")
    public ResponseEntity<Void> respondRequest(@PathVariable Long requestId,
            @RequestBody FriendResponse response,
            @AuthenticationPrincipal User user) {
        friendService.respondRequest(requestId, user.getId(), response.accept());
        return ResponseEntity.ok().build();
    }

    // 好友列表（含在线状态）
    @GetMapping
    public List<FriendView> listFriends(@AuthenticationPrincipal User user) {
        return friendService.listFriends(user.getId());
    }

    // 待处理请求列表
    @GetMapping("/requests/pending")
    public List<FriendRequestView> pendingRequests(@AuthenticationPrincipal User user) {
        return friendService.pendingRequests(user.getId());
    }

    // 删除好友
    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> removeFriend(@PathVariable String friendId,
            @AuthenticationPrincipal User user) {
        friendService.removeFriend(user.getId(), friendId);
        return ResponseEntity.ok().build();
    }

    // 邀请好友进入房间
    @PostMapping("/{friendId}/invite")
    public ResponseEntity<Void> inviteToRoom(@PathVariable String friendId,
            @RequestBody RoomInvite invite,
            @AuthenticationPrincipal User user) {
        friendService.inviteToRoom(user.getId(), friendId, invite.roomId(), invite.gameId());
        return ResponseEntity.ok().build();
    }

    // 与某好友的共同对局历史
    @GetMapping("/{friendId}/games")
    public List<SharedGameRecord> sharedGames(@PathVariable String friendId,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page) {
        return friendService.sharedGames(user.getId(), friendId, page);
    }
}
```

### 4.2 DTO

```java
public record FriendRequest(String targetUserId) {}
public record FriendResponse(boolean accept) {}
public record RoomInvite(String roomId, String gameId) {}

public record UserBrief(
    String id, String nickname, String avatar, int level, boolean isFriend
) {}

public record FriendView(
    String userId, String nickname, String avatar, int level,
    String onlineStatus,    // "ONLINE" | "IN_GAME" | "OFFLINE"
    String currentRoomId,   // 正在游戏的房间（如在游戏中）
    String currentGameId
) {}

public record FriendRequestView(
    Long requestId, String fromUserId, String fromNickname,
    String fromAvatar, LocalDateTime createdAt
) {}

public record SharedGameRecord(
    String roomId, String gameId, String winner,
    LocalDateTime playedAt, int playerCount
) {}
```

### 4.3 Service 核心逻辑

```java
@Service
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final PlayerConnectionService connectionService;
    private final GamePushService pushService;

    public void sendRequest(String userId, String targetId) {
        if (userId.equals(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能添加自己为好友");
        }
        if (friendshipRepository.areFriends(userId, targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "已经是好友了");
        }
        // 检查是否已有待处理请求
        Optional<Friendship> existing = friendshipRepository.findByUserIdAndFriendId(userId, targetId);
        if (existing.isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "已发送过好友请求");
        }
        // 检查对方是否已向我发送请求（自动互加）
        Optional<Friendship> reverse = friendshipRepository.findByUserIdAndFriendId(targetId, userId);
        if (reverse.isPresent() && reverse.get().getStatus() == FriendshipStatus.PENDING) {
            reverse.get().setStatus(FriendshipStatus.ACCEPTED);
            friendshipRepository.save(reverse.get());
            pushFriendNotification(targetId, "FRIEND_ACCEPTED", userId);
            return;
        }

        Friendship friendship = new Friendship();
        friendship.setUserId(userId);
        friendship.setFriendId(targetId);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(friendship);

        // 实时推送好友请求通知
        pushFriendNotification(targetId, "FRIEND_REQUEST", userId);
    }

    public List<FriendView> listFriends(String userId) {
        List<Friendship> friendships = friendshipRepository.findAcceptedFriends(userId);
        return friendships.stream().map(f -> {
            String friendId = f.getUserId().equals(userId) ? f.getFriendId() : f.getUserId();
            User friend = userRepository.findById(friendId).orElse(null);
            if (friend == null) return null;

            String onlineStatus = connectionService.isOnline(friendId) ? "ONLINE" : "OFFLINE";
            // 检查是否在游戏中
            String currentRoomId = connectionService.getCurrentRoom(friendId);
            if (currentRoomId != null) onlineStatus = "IN_GAME";

            return new FriendView(friendId, friend.getNickname(), friend.getAvatar(),
                friend.getLevel(), onlineStatus, currentRoomId, null);
        }).filter(Objects::nonNull).toList();
    }

    public void inviteToRoom(String userId, String friendId, String roomId, String gameId) {
        if (!friendshipRepository.areFriends(userId, friendId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能邀请好友");
        }
        User inviter = userRepository.findById(userId).orElseThrow();
        pushService.pushPrivate(friendId, new PrivateEvent("ROOM_INVITE", Map.of(
            "fromNickname", inviter.getNickname(),
            "fromAvatar", inviter.getAvatar(),
            "roomId", roomId,
            "gameId", gameId
        )));
    }
}
```

## 5. 前端设计

### 5.1 好友面板（侧边栏抽屉）

```tsx
// components/FriendPanel.tsx — 全局可访问的好友面板
const FriendPanel = () => {
  const { data: friends = [] } = useQuery({
    queryKey: ['friends'],
    queryFn: friendApi.list,
    refetchInterval: 30000, // 30s 刷新在线状态
  });

  const { data: pendingRequests = [] } = useQuery({
    queryKey: ['friends', 'pending'],
    queryFn: friendApi.pendingRequests,
  });

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="relative">
          <Users className="h-5 w-5" />
          {pendingRequests.length > 0 && (
            <span className="absolute -top-1 -right-1 h-4 w-4 rounded-full
              bg-red-500 text-white text-[10px] flex items-center justify-center">
              {pendingRequests.length}
            </span>
          )}
        </Button>
      </SheetTrigger>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>好友</SheetTitle>
        </SheetHeader>
        <Tabs defaultValue="online">
          <TabsList className="w-full">
            <TabsTrigger value="online">在线</TabsTrigger>
            <TabsTrigger value="all">全部</TabsTrigger>
            <TabsTrigger value="requests">
              请求 {pendingRequests.length > 0 && `(${pendingRequests.length})`}
            </TabsTrigger>
          </TabsList>
          {/* Tab 内容 */}
        </Tabs>
      </SheetContent>
    </Sheet>
  );
};
```

### 5.2 邀请通知 Toast

```tsx
// 通过 WebSocket 接收邀请
useGameSocket({
  onPrivate: (event) => {
    if (event.type === "ROOM_INVITE") {
      toast(
        <div className="flex items-center gap-3">
          <Avatar className="h-8 w-8">
            <AvatarImage src={event.payload.fromAvatar} />
          </Avatar>
          <div>
            <div className="font-medium">{event.payload.fromNickname} 邀请你加入游戏</div>
            <div className="flex gap-2 mt-1">
              <Button size="sm" onClick={() => navigate(`/room/${event.payload.gameId}/${event.payload.roomId}`)}>
                加入
              </Button>
              <Button size="sm" variant="ghost">忽略</Button>
            </div>
          </div>
        </div>,
        { duration: 15000 }
      );
    }
  }
});
```

### 5.3 游戏内添加好友

在游戏结算页面和玩家列表中提供"添加好友"按钮：

```tsx
// 结算页面的玩家卡片中
{!player.isAi && player.playerId !== myPlayerId && (
  <Button size="sm" variant="ghost" onClick={() => sendFriendRequest(player.playerId)}>
    <UserPlus className="h-3 w-3 mr-1" /> 加好友
  </Button>
)}
```

## 6. 在线状态同步

好友在线状态通过两种方式同步：

1. **REST 轮询**：好友列表每 30 秒刷新一次
2. **WebSocket 推送**：好友上线/下线/进入游戏时实时推送

```java
// 在 WebSocketEventListener 中
@EventListener
public void handleConnect(SessionConnectEvent event) {
    // ... 现有逻辑 ...
    // 通知该用户的所有在线好友
    List<String> friendIds = friendService.getOnlineFriendIds(playerId);
    for (String friendId : friendIds) {
        pushService.pushPrivate(friendId, new PrivateEvent("FRIEND_ONLINE",
            Map.of("friendId", playerId)));
    }
}
```

## 7. 数据库变更

新增 `friendships` 表（见 3.1 节）。

## 8. 测试要点

- [ ] 发送/接受/拒绝好友请求
- [ ] 双向请求自动互加
- [ ] 好友列表在线状态正确
- [ ] 游戏邀请推送与跳转
- [ ] 删除好友双向生效
- [ ] 搜索用户功能
- [ ] 好友数量上限（建议 200）
- [ ] 未登录用户（游客）无法使用好友功能
