# 模块 06：快速匹配

> 优先级：P1 | 阶段：第二阶段 | 依赖：模块 01（WebSocket） | 被依赖：无

## 1. 背景与目标

当前开局流程：首页选游戏 → 房间列表 → 创建房间 → 手动添加 AI → 开始游戏。对于"一个人想快速玩一局"的场景，步骤过多。

目标：提供一键快速匹配，自动完成房间创建、AI 补位、开局，将"想玩到玩上"的路径缩短到一次点击。

## 2. 匹配模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| 快速开始 | 自动创建房间 + AI 补满 + 立即开局 | 单人想快速体验 |
| 匹配真人 | 加入匹配队列，凑够真人后开局，超时 AI 补位 | 想和真人玩 |
| 加入现有 | 自动加入一个有空位的等待中房间 | 随便玩玩 |

### 2.1 快速开始流程

```
用户点击"快速开始"
  │
  ├─► 选择游戏类型（如已在游戏页则跳过）
  │
  ├─► 后端 POST /api/games/{gameId}/quick-start
  │     │
  │     ├─► 创建房间（默认配置）
  │     ├─► 将用户加入座位 0
  │     ├─► 用随机 Persona 填满剩余座位
  │     ├─► 自动开始游戏
  │     └─► 返回 roomId + gameState
  │
  └─► 前端跳转到游戏房间页面
```

### 2.2 匹配真人流程

```
用户点击"匹配真人"
  │
  ├─► 加入匹配队列 POST /api/matchmaking/join
  │     │
  │     ├─► 返回 matchId + 预估等待时间
  │     └─► 前端显示匹配等待界面
  │
  ├─► 等待中...（WebSocket 推送匹配状态）
  │     │
  │     ├─► 凑够最低人数 → 创建房间 → 推送 MATCH_FOUND
  │     │
  │     └─► 超过等待阈值（60s）
  │           ├─► 提示"是否用 AI 补位开始？"
  │           └─► 用户确认 → AI 补位 → 开局
  │
  └─► 匹配成功 → 跳转到游戏房间
```

## 3. 后端设计

### 3.1 快速开始接口

```java
// QuickMatchController.java
@RestController
@RequestMapping("/api/games/{gameId}")
public class QuickMatchController {

    private final QuickMatchService quickMatchService;

    @PostMapping("/quick-start")
    public ResponseEntity<QuickStartResponse> quickStart(
            @PathVariable String gameId,
            @RequestBody(required = false) QuickStartRequest request,
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "X-Player-Id", required = false) String playerIdHeader) {
        return ResponseEntity.ok(quickMatchService.quickStart(gameId, user, playerIdHeader, request));
    }
}

public record QuickStartRequest(
    Integer playerCount,        // 期望人数（null 则使用默认值）
    String difficulty,          // AI 难度 "EASY" | "NORMAL" | "HARD"
    Map<String, Object> config  // 额外配置（如卧底数量、是否有白板等）
) {}

public record QuickStartResponse(
    String roomId,
    String gameId,
    GameStateResponse state
) {}
```

### 3.2 快速开始服务

```java
@Service
public class QuickMatchService {

    private final RoomService roomService;
    private final GamePlayService gamePlayService;
    private final PersonaService personaService;

    private static final Map<String, Integer> DEFAULT_PLAYER_COUNT = Map.of(
        "undercover", 6,
        "werewolf", 8
    );

    public QuickStartResponse quickStart(String gameId, User user,
            String playerIdHeader, QuickStartRequest request) {

        int targetCount = request != null && request.playerCount() != null
            ? request.playerCount()
            : DEFAULT_PLAYER_COUNT.getOrDefault(gameId, 6);

        // 1. 创建房间
        Room room = roomService.createRoom(gameId, "快速对局", targetCount, false, null, null);

        // 2. 加入玩家
        String displayName = user != null ? user.getNickname() : "玩家";
        roomService.join(room.getId(), gameId, displayName, user, playerIdHeader);

        // 3. AI 补位
        List<Persona> personas = personaService.listAll();
        String difficulty = request != null ? request.difficulty() : "NORMAL";
        Collections.shuffle(personas);
        int aiNeeded = targetCount - 1;
        for (int i = 0; i < aiNeeded && i < personas.size(); i++) {
            roomService.addAi(room.getId(), gameId, personas.get(i).getId());
        }

        // 4. 自动开局
        String actorId = resolvePlayerId(room, user, playerIdHeader);
        GameStateResponse state = gamePlayService.start(gameId, room.getId(), user, playerIdHeader);

        return new QuickStartResponse(room.getId(), gameId, state);
    }
}
```

### 3.3 匹配队列服务

```java
@Service
public class MatchmakingService {

    // 按游戏类型维护匹配队列
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<MatchEntry>> queues
        = new ConcurrentHashMap<>();

    public record MatchEntry(
        String playerId,
        String displayName,
        String gameId,
        LocalDateTime joinedAt
    ) {}

    public record MatchResult(
        String matchId,
        String status,    // "WAITING" | "MATCHED" | "TIMEOUT"
        String roomId,
        int queuePosition,
        int estimatedWaitSeconds
    ) {}

    public MatchResult joinQueue(String gameId, String playerId, String displayName) {
        ConcurrentLinkedQueue<MatchEntry> queue =
            queues.computeIfAbsent(gameId, k -> new ConcurrentLinkedQueue<>());

        // 防止重复加入
        if (queue.stream().anyMatch(e -> e.playerId().equals(playerId))) {
            return new MatchResult(null, "WAITING", null, getPosition(queue, playerId), estimateWait(queue, gameId));
        }

        queue.add(new MatchEntry(playerId, displayName, gameId, LocalDateTime.now()));

        // 检查是否凑够人数
        int minPlayers = getMinPlayers(gameId);
        if (queue.size() >= minPlayers) {
            return createMatchedRoom(gameId, queue, minPlayers);
        }

        return new MatchResult(null, "WAITING", null, queue.size(), estimateWait(queue, gameId));
    }

    public void leaveQueue(String gameId, String playerId) {
        ConcurrentLinkedQueue<MatchEntry> queue = queues.get(gameId);
        if (queue != null) {
            queue.removeIf(e -> e.playerId().equals(playerId));
        }
    }

    // 定时任务：检查超时的匹配请求
    @Scheduled(fixedRate = 5000)
    public void checkTimeouts() {
        queues.forEach((gameId, queue) -> {
            queue.stream()
                .filter(e -> Duration.between(e.joinedAt(), LocalDateTime.now()).toSeconds() > 60)
                .forEach(e -> {
                    // 推送超时通知，建议 AI 补位
                    gamePushService.pushPrivate(e.playerId(),
                        new PrivateEvent("MATCH_TIMEOUT", Map.of("gameId", gameId)));
                });
        });
    }
}
```

## 4. 前端设计

### 4.1 快速开始按钮

在首页游戏卡片和房间列表页添加快速开始入口：

```tsx
// 首页游戏卡片中
<div className="flex gap-2">
  <Button className="flex-1" onClick={() => navigate(`/game/${game.id}`)}>
    进入大厅
  </Button>
  <Button variant="secondary" onClick={() => handleQuickStart(game.id)}>
    <Zap className="h-4 w-4 mr-1" /> 快速开始
  </Button>
</div>
```

### 4.2 快速开始配置弹窗

```tsx
const QuickStartDialog = ({ gameId, open, onClose }: Props) => {
  const [playerCount, setPlayerCount] = useState(
    gameId === "werewolf" ? 8 : 6);
  const [difficulty, setDifficulty] = useState("NORMAL");

  const quickStartMutation = useMutation({
    mutationFn: () => api.post(`/api/games/${gameId}/quick-start`, {
      playerCount, difficulty
    }),
    onSuccess: (data) => {
      navigate(`/room/${gameId}/${data.roomId}`);
    },
  });

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>快速开始</DialogTitle>
          <DialogDescription>AI 自动补位，即刻开局</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div>
            <Label>人数</Label>
            <Select value={String(playerCount)} onValueChange={v => setPlayerCount(Number(v))}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {getPlayerCountOptions(gameId).map(n => (
                  <SelectItem key={n} value={String(n)}>{n} 人局</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div>
            <Label>AI 难度</Label>
            <Select value={difficulty} onValueChange={setDifficulty}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="EASY">简单 — 适合新手</SelectItem>
                <SelectItem value="NORMAL">普通 — 正常对局</SelectItem>
                <SelectItem value="HARD">困难 — 有挑战性</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button onClick={() => quickStartMutation.mutate()}
            disabled={quickStartMutation.isPending} className="w-full">
            {quickStartMutation.isPending ? (
              <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> 创建中...</>
            ) : (
              <><Zap className="h-4 w-4 mr-2" /> 开始游戏</>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
```

### 4.3 匹配等待界面

```tsx
const MatchmakingWait = ({ gameId }: { gameId: string }) => {
  const [status, setStatus] = useState<MatchResult | null>(null);
  const [elapsed, setElapsed] = useState(0);

  // WebSocket 监听匹配结果
  useEffect(() => {
    // 订阅 /user/queue/private 等待 MATCH_FOUND 或 MATCH_TIMEOUT
  }, []);

  return (
    <div className="flex flex-col items-center justify-center min-h-[400px] space-y-6">
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ repeat: Infinity, duration: 3, ease: "linear" }}
      >
        <Search className="h-16 w-16 text-blue-500" />
      </motion.div>

      <div className="text-center">
        <h2 className="text-xl font-bold">正在匹配玩家...</h2>
        <p className="text-muted-foreground mt-1">
          已等待 {elapsed}s · 队列中第 {status?.queuePosition || "?"} 位
        </p>
      </div>

      <div className="flex gap-3">
        <Button variant="outline" onClick={handleCancel}>取消匹配</Button>
        <Button variant="secondary" onClick={handleFillWithAi}>
          <Bot className="h-4 w-4 mr-2" /> AI 补位开始
        </Button>
      </div>
    </div>
  );
};
```

## 5. API 汇总

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/games/{gameId}/quick-start` | 快速开始（创建+补位+开局） |
| POST | `/api/matchmaking/{gameId}/join` | 加入匹配队列 |
| DELETE | `/api/matchmaking/{gameId}/leave` | 离开匹配队列 |
| GET | `/api/matchmaking/{gameId}/status` | 查询匹配状态 |

## 6. 数据库变更

无。匹配队列使用内存数据结构，不持久化。如果未来需要跨实例匹配，可迁移到 Redis。

## 7. 配置项

```yaml
matchmaking:
  default-player-count:
    undercover: 6
    werewolf: 8
  timeout-seconds: 60          # 匹配超时阈值
  min-human-players: 2         # 最少真人数量（匹配模式）
  check-interval-seconds: 5    # 队列检查间隔
```

## 8. 测试要点

- [ ] 快速开始：一键创建房间 + AI 补位 + 开局成功
- [ ] 快速开始：不同人数和难度配置
- [ ] 匹配队列：加入/离开/超时
- [ ] 匹配成功后正确创建房间并通知所有匹配玩家
- [ ] 并发匹配请求的线程安全
- [ ] 匹配超时后 AI 补位流程
