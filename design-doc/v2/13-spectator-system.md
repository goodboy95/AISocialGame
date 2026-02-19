# 模块 13：观战系统

> 优先级：P2 | 阶段：第三阶段 | 依赖：模块 01（WebSocket）、模块 09（对局回放） | 被依赖：无

## 1. 背景与目标

当前没有观战功能。玩家只能作为参与者进入房间。

目标：支持旁观者模式，可以实时观看正在进行的游戏，提供上帝视角（看到所有身份）和跟随视角（看到某个玩家的视角）。为未来的锦标赛和社区内容提供基础。

## 2. 功能设计

| 功能 | 说明 |
|------|------|
| 加入观战 | 在房间列表中点击"观战"进入旁观模式 |
| 上帝视角 | 看到所有玩家的身份、夜晚行动、投票详情 |
| 跟随视角 | 选择一个玩家，只看到该玩家能看到的信息 |
| 观战聊天 | 观战者之间的独立聊天频道（不泄露给玩家） |
| 观战人数 | 显示当前观战人数 |
| 观战入口 | 房间列表标记"可观战"，好友正在游戏时可点击观战 |

## 3. 后端设计

### 3.1 观战者管理

```java
@Service
public class SpectatorService {

    // roomId -> Set<spectatorId>
    private final ConcurrentHashMap<String, Set<String>> spectators = new ConcurrentHashMap<>();

    public void joinSpectate(String roomId, String spectatorId) {
        spectators.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(spectatorId);
        // 通知房间内玩家观战人数变化
        pushService.pushStateChange(roomId,
            new GameStateEvent("SPECTATOR_JOIN", null, 0, null,
                Map.of("spectatorCount", getSpectatorCount(roomId))));
    }

    public void leaveSpectate(String roomId, String spectatorId) {
        Set<String> set = spectators.get(roomId);
        if (set != null) {
            set.remove(spectatorId);
            if (set.isEmpty()) spectators.remove(roomId);
        }
    }

    public int getSpectatorCount(String roomId) {
        Set<String> set = spectators.get(roomId);
        return set != null ? set.size() : 0;
    }

    public boolean isSpectating(String roomId, String userId) {
        Set<String> set = spectators.get(roomId);
        return set != null && set.contains(userId);
    }
}
```

### 3.2 观战状态接口

```java
@RestController
@RequestMapping("/api/games/{gameId}/rooms/{roomId}/spectate")
public class SpectatorController {

    @PostMapping("/join")
    public ResponseEntity<SpectateResponse> joinSpectate(
            @PathVariable String gameId, @PathVariable String roomId,
            @AuthenticationPrincipal User user) {
        spectatorService.joinSpectate(roomId, user.getId());
        return ResponseEntity.ok(new SpectateResponse(roomId, "joined"));
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveSpectate(
            @PathVariable String gameId, @PathVariable String roomId,
            @AuthenticationPrincipal User user) {
        spectatorService.leaveSpectate(roomId, user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/state")
    public GameStateResponse spectateState(
            @PathVariable String gameId, @PathVariable String roomId,
            @RequestParam(defaultValue = "GOD") String viewMode,
            @RequestParam(required = false) String followPlayerId,
            @AuthenticationPrincipal User user) {
        if (!spectatorService.isSpectating(roomId, user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "请先加入观战");
        }
        return spectateService.buildSpectateView(gameId, roomId, viewMode, followPlayerId);
    }
}
```

### 3.3 观战视图构建

```java
@Service
public class SpectateViewService {

    public GameStateResponse buildSpectateView(String gameId, String roomId,
            String viewMode, String followPlayerId) {
        Room room = roomService.getRoom(roomId);
        GameState state = gameStateRepository.findById(roomId).orElse(null);
        if (state == null) {
            return buildWaitingView(room);
        }

        GameEngine engine = engineRegistry.getEngine(gameId);

        if ("GOD".equals(viewMode)) {
            // 上帝视角：所有信息可见
            return buildGodView(state, room);
        } else if ("FOLLOW".equals(viewMode) && followPlayerId != null) {
            // 跟随视角：等同于该玩家看到的信息
            return engine.buildView(state, room, followPlayerId);
        }

        // 默认上帝视角
        return buildGodView(state, room);
    }

    private GameStateResponse buildGodView(GameState state, Room room) {
        // 所有玩家身份可见、所有夜晚行动可见、投票详情可见
        List<GamePlayerView> players = state.getPlayers().stream()
            .map(p -> new GamePlayerView(
                p.getPlayerId(), p.getDisplayName(), p.getSeatNumber(),
                p.isAi(), p.getPersonaId(), p.getAvatar(), p.isAlive(),
                p.getRole(),  // 始终可见
                p.getWord()   // 始终可见
            ))
            .toList();

        Map<String, Object> extra = new HashMap<>(state.getData());
        extra.put("isSpectatorView", true);
        extra.put("viewMode", "GOD");

        return new GameStateResponse(
            room.getId(), room.getGameId(), state.getPhase(),
            state.getRoundNumber(), state.getCurrentSeat(), null, null,
            null, null, null, null, state.getPhaseEndsAt(),
            players, state.getLogs(), extra, voteMap(state), null
        );
    }
}
```

### 3.4 WebSocket 订阅

观战者订阅与玩家相同的房间 topic，但额外订阅观战专用频道：

| Topic | 用途 |
|-------|------|
| `/topic/room/{roomId}/state` | 游戏状态变更（与玩家共享） |
| `/topic/room/{roomId}/spectator-chat` | 观战者聊天（玩家不可见） |

## 4. 前端设计

### 4.1 观战入口

在房间列表中：

```tsx
// RoomList.tsx 中的房间卡片
{room.status === "PLAYING" && (
  <Button variant="ghost" size="sm"
    onClick={() => navigate(`/spectate/${gameId}/${room.id}`)}>
    <Eye className="h-4 w-4 mr-1" /> 观战
    {room.spectatorCount > 0 && (
      <Badge variant="secondary" className="ml-1 text-[10px]">
        {room.spectatorCount}
      </Badge>
    )}
  </Button>
)}
```

在好友列表中：

```tsx
// FriendPanel 中
{friend.onlineStatus === "IN_GAME" && (
  <Button size="sm" variant="ghost"
    onClick={() => navigate(`/spectate/${friend.currentGameId}/${friend.currentRoomId}`)}>
    <Eye className="h-3 w-3 mr-1" /> 观战
  </Button>
)}
```

### 4.2 观战页面

```tsx
// pages/Spectate.tsx
const Spectate = () => {
  const { gameId, roomId } = useParams();
  const [viewMode, setViewMode] = useState<"GOD" | "FOLLOW">("GOD");
  const [followPlayer, setFollowPlayer] = useState<string | null>(null);

  // 加入观战
  useEffect(() => {
    spectateApi.join(gameId!, roomId!);
    return () => { spectateApi.leave(gameId!, roomId!); };
  }, [gameId, roomId]);

  // 获取观战状态
  const { data: state } = useQuery({
    queryKey: ['spectate-state', roomId, viewMode, followPlayer],
    queryFn: () => spectateApi.state(gameId!, roomId!, viewMode, followPlayer),
    refetchInterval: 2000, // WebSocket 接入后可移除
  });

  return (
    <div className="space-y-4">
      {/* 观战控制栏 */}
      <div className="flex items-center justify-between bg-slate-900 text-white px-4 py-2 rounded-lg">
        <div className="flex items-center gap-2">
          <Eye className="h-4 w-4" />
          <span className="text-sm font-medium">观战模式</span>
        </div>
        <div className="flex items-center gap-3">
          <Select value={viewMode} onValueChange={(v) => setViewMode(v as any)}>
            <SelectTrigger className="w-32 h-8 bg-slate-800 border-slate-700 text-white">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="GOD">上帝视角</SelectItem>
              <SelectItem value="FOLLOW">跟随视角</SelectItem>
            </SelectContent>
          </Select>
          {viewMode === "FOLLOW" && (
            <Select value={followPlayer || ""} onValueChange={setFollowPlayer}>
              <SelectTrigger className="w-32 h-8 bg-slate-800 border-slate-700 text-white">
                <SelectValue placeholder="选择玩家" />
              </SelectTrigger>
              <SelectContent>
                {state?.players?.map(p => (
                  <SelectItem key={p.playerId} value={p.playerId}>
                    {p.displayName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>
      </div>

      {/* 复用游戏房间组件，传入 spectator 标记 */}
      <GameBoard state={state} isSpectator={true} viewMode={viewMode} />

      {/* 观战者聊天 */}
      <SpectatorChat roomId={roomId!} />
    </div>
  );
};
```

### 4.3 上帝视角增强

上帝视角下，玩家卡片显示完整身份信息：

```tsx
// 观战模式下的玩家卡片
const SpectatorPlayerCard = ({ player }: { player: GamePlayerView }) => (
  <div className={`p-2 rounded-lg border ${getRoleBorderColor(player.role)}`}>
    <Avatar className="h-10 w-10">
      <AvatarImage src={player.avatar} />
      <AvatarFallback>{player.displayName[0]}</AvatarFallback>
    </Avatar>
    <div className="font-medium text-sm">{player.displayName}</div>
    {/* 上帝视角：始终显示身份 */}
    <Badge className={getRoleBadgeColor(player.role)}>
      {getRoleDisplayName(player.role)}
    </Badge>
    {player.word && (
      <div className="text-[10px] text-muted-foreground">"{player.word}"</div>
    )}
    {!player.alive && <Badge variant="destructive" className="text-[10px]">出局</Badge>}
  </div>
);
```

## 5. 路由

```
/spectate/:gameId/:roomId — 观战页面
```

## 6. 房间列表扩展

`Room` 响应中新增观战相关字段：

```java
// RoomService 返回房间列表时附加观战信息
public record RoomListItem(
    // ... 现有字段 ...
    int spectatorCount,
    boolean spectateAllowed    // 是否允许观战（可在创建房间时配置）
) {}
```

## 7. 数据库变更

```sql
ALTER TABLE rooms ADD COLUMN spectate_allowed BOOLEAN DEFAULT TRUE;
```

## 8. 配置项

```yaml
spectator:
  max-per-room: 20           # 每个房间最大观战人数
  god-view-enabled: true     # 是否允许上帝视角
  chat-enabled: true         # 是否允许观战者聊天
```

## 9. 测试要点

- [ ] 加入/离开观战
- [ ] 上帝视角：所有身份和行动可见
- [ ] 跟随视角：信息与被跟随玩家一致
- [ ] 观战者聊天不泄露给玩家
- [ ] 观战人数实时更新
- [ ] 游戏结束后观战自动结束
- [ ] 观战者不能执行游戏操作
- [ ] 房间列表正确显示观战入口和人数
