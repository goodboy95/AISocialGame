# 模块 09：对局回放

> 优先级：P1 | 阶段：第二阶段 | 依赖：模块 03（结算系统） | 被依赖：模块 13（观战系统）

## 1. 背景与目标

当前游戏结束后，`game_states` 中的 `logs` 字段保留了文本日志，但没有结构化的事件流，也没有回放功能。玩家无法回顾精彩对局。

目标：记录结构化的游戏事件流，提供可回放的对局记录，支持上帝视角（全信息）和玩家视角（受限信息）。

## 2. 事件记录模型

### 2.1 游戏事件表

```sql
CREATE TABLE game_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id     VARCHAR(36) NOT NULL,
    game_id     VARCHAR(20) NOT NULL,
    seq         INT NOT NULL,                -- 事件序号（房间内递增）
    event_type  VARCHAR(50) NOT NULL,        -- 事件类型
    phase       VARCHAR(30),                 -- 发生时的阶段
    round       INT DEFAULT 0,              -- 发生时的轮次
    actor_id    VARCHAR(36),                 -- 行为主体
    target_id   VARCHAR(36),                 -- 行为目标
    data        JSON,                        -- 事件详细数据
    visibility  VARCHAR(20) DEFAULT 'PUBLIC', -- PUBLIC | PRIVATE | GOD
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_seq (room_id, seq),
    INDEX idx_room_type (room_id, event_type)
);
```

### 2.2 对局存档表

```sql
CREATE TABLE game_archives (
    id              VARCHAR(36) PRIMARY KEY,
    room_id         VARCHAR(36) NOT NULL,
    game_id         VARCHAR(20) NOT NULL,
    winner          VARCHAR(30),
    player_count    INT,
    total_rounds    INT,
    duration_seconds INT,                    -- 对局时长
    players         JSON,                    -- 完整玩家信息（含身份）
    summary         VARCHAR(500),            -- AI 生成的对局摘要
    featured        BOOLEAN DEFAULT FALSE,   -- 是否精选
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room (room_id),
    INDEX idx_game (game_id),
    INDEX idx_created (created_at DESC)
);
```

### 2.3 事件类型枚举

```java
public enum GameEventType {
    // 通用事件
    GAME_START,         // 游戏开始
    PHASE_CHANGE,       // 阶段切换
    ROLE_ASSIGNED,      // 角色分配（GOD 可见）
    SPEECH,             // 发言
    VOTE_CAST,          // 投票
    VOTE_RESULT,        // 投票结果
    PLAYER_ELIMINATED,  // 玩家淘汰
    GAME_END,           // 游戏结束

    // 狼人杀专用
    WOLF_KILL,          // 狼人击杀（GOD 可见）
    SEER_CHECK,         // 预言家查验（GOD 可见）
    WITCH_SAVE,         // 女巫救人（GOD 可见）
    WITCH_POISON,       // 女巫毒人（GOD 可见）
    NIGHT_RESULT,       // 夜晚结果（PUBLIC）

    // 谁是卧底专用
    WORD_ASSIGNED,      // 词语分配（GOD 可见）
}
```

## 3. 后端实现

### 3.1 事件记录服务

```java
@Service
public class GameEventRecorder {

    private final GameEventRepository eventRepository;
    private final AtomicInteger seqCounter = new AtomicInteger(0);

    /**
     * 记录游戏事件
     */
    public void record(String roomId, String gameId, GameEventType type,
                       String phase, int round, String actorId, String targetId,
                       Map<String, Object> data, EventVisibility visibility) {
        GameEvent event = new GameEvent();
        event.setRoomId(roomId);
        event.setGameId(gameId);
        event.setSeq(seqCounter.incrementAndGet());
        event.setEventType(type.name());
        event.setPhase(phase);
        event.setRound(round);
        event.setActorId(actorId);
        event.setTargetId(targetId);
        event.setData(data);
        event.setVisibility(visibility.name());
        eventRepository.save(event);
    }

    /**
     * 游戏结束时创建存档
     */
    public void archive(String roomId, GameState state, Room room) {
        GameArchive archive = new GameArchive();
        archive.setId(UUID.randomUUID().toString());
        archive.setRoomId(roomId);
        archive.setGameId(state.getGameId());
        archive.setWinner((String) state.getData().get("winner"));
        archive.setPlayerCount(state.getPlayers().size());
        archive.setTotalRounds(state.getRoundNumber());
        archive.setPlayers(state.getPlayers()); // 完整信息，含角色
        archive.setDurationSeconds(calculateDuration(state));
        archiveRepository.save(archive);
    }
}
```

### 3.2 在 GamePlayService 中埋点

在关键操作处调用 `GameEventRecorder`：

```java
// start() 方法中
eventRecorder.record(roomId, gameId, GameEventType.GAME_START, ...);
for (GamePlayerState p : state.getPlayers()) {
    eventRecorder.record(roomId, gameId, GameEventType.ROLE_ASSIGNED,
        "SETUP", 0, p.getPlayerId(), null,
        Map.of("role", p.getRole(), "word", p.getWord() != null ? p.getWord() : ""),
        EventVisibility.GOD);
}

// speak() 方法中
eventRecorder.record(roomId, gameId, GameEventType.SPEECH,
    state.getPhase(), state.getRoundNumber(), actorId, null,
    Map.of("content", content), EventVisibility.PUBLIC);

// vote() 方法中
eventRecorder.record(roomId, gameId, GameEventType.VOTE_CAST,
    state.getPhase(), state.getRoundNumber(), actorId, targetId,
    Map.of("abstain", request.isAbstain()), EventVisibility.PUBLIC);

// finishGame() 方法中
eventRecorder.record(roomId, gameId, GameEventType.GAME_END, ...);
eventRecorder.archive(roomId, state, room);
```

### 3.3 回放 API

```java
@RestController
@RequestMapping("/api/replays")
public class ReplayController {

    // 获取对局存档列表
    @GetMapping
    public Page<GameArchive> listArchives(
            @RequestParam(required = false) String gameId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return replayService.listArchives(gameId, page, size);
    }

    // 获取对局事件流（回放数据）
    @GetMapping("/{archiveId}/events")
    public ReplayData getReplayEvents(
            @PathVariable String archiveId,
            @RequestParam(defaultValue = "GOD") String viewMode) {
        return replayService.getReplayData(archiveId, viewMode);
    }

    // 获取我的对局历史
    @GetMapping("/my")
    public Page<GameArchive> myReplays(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page) {
        return replayService.playerReplays(user.getId(), page);
    }
}

public record ReplayData(
    GameArchive archive,
    List<ReplayEvent> events,
    Map<String, PlayerReveal> players  // 完整玩家信息
) {}

public record ReplayEvent(
    int seq,
    String eventType,
    String phase,
    int round,
    String actorName,
    String targetName,
    Map<String, Object> data,
    long timestampMs       // 相对于游戏开始的毫秒数
) {}
```

### 3.4 视角过滤

```java
public List<ReplayEvent> filterByView(List<GameEvent> events, String viewMode, String viewerId) {
    return events.stream()
        .filter(e -> {
            if ("GOD".equals(viewMode)) return true;  // 上帝视角看全部
            if ("PUBLIC".equals(e.getVisibility())) return true;
            if ("PRIVATE".equals(e.getVisibility()) && e.getActorId().equals(viewerId)) return true;
            return false;
        })
        .map(this::toReplayEvent)
        .toList();
}
```

## 4. 前端回放播放器

### 4.1 组件结构

```
ReplayPlayer
├── ReplayHeader         对局基本信息
├── ReplayBoard          玩家座位可视化
├── ReplayTimeline       事件时间线（可拖拽）
├── ReplayEventList      事件详情列表
├── ReplayControls       播放/暂停/快进/视角切换
└── ReplaySpeedSelector  播放速度选择
```

### 4.2 播放控制 Hook

```typescript
// hooks/useReplayPlayer.ts
interface UseReplayPlayerOptions {
  events: ReplayEvent[];
  speed?: number; // 1x, 2x, 4x
}

export function useReplayPlayer({ events, speed = 1 }: UseReplayPlayerOptions) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [visibleEvents, setVisibleEvents] = useState<ReplayEvent[]>([]);

  useEffect(() => {
    if (!isPlaying || currentIndex >= events.length) return;

    const currentEvent = events[currentIndex];
    const nextEvent = events[currentIndex + 1];

    // 计算到下一个事件的间隔
    const delay = nextEvent
      ? Math.max(200, (nextEvent.timestampMs - currentEvent.timestampMs) / speed)
      : 0;

    const timer = setTimeout(() => {
      setVisibleEvents(prev => [...prev, currentEvent]);
      setCurrentIndex(prev => prev + 1);
    }, delay);

    return () => clearTimeout(timer);
  }, [isPlaying, currentIndex, speed]);

  const play = () => setIsPlaying(true);
  const pause = () => setIsPlaying(false);
  const seekTo = (index: number) => {
    setCurrentIndex(index);
    setVisibleEvents(events.slice(0, index));
  };
  const reset = () => { setCurrentIndex(0); setVisibleEvents([]); };

  return {
    currentIndex, isPlaying, visibleEvents,
    play, pause, seekTo, reset,
    progress: events.length > 0 ? currentIndex / events.length : 0,
  };
}
```

### 4.3 时间线组件

```tsx
const ReplayTimeline = ({ events, currentIndex, onSeek }: Props) => (
  <div className="relative">
    {/* 进度条 */}
    <div className="h-2 bg-slate-200 rounded-full cursor-pointer"
      onClick={(e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const ratio = (e.clientX - rect.left) / rect.width;
        onSeek(Math.floor(ratio * events.length));
      }}>
      <div className="h-full bg-blue-500 rounded-full transition-all"
        style={{ width: `${(currentIndex / events.length) * 100}%` }} />
    </div>

    {/* 阶段标记 */}
    <div className="flex justify-between mt-1">
      {getPhaseMarkers(events).map((marker, idx) => (
        <div key={idx} className="text-[10px] text-muted-foreground"
          style={{ left: `${marker.position}%` }}>
          {marker.label}
        </div>
      ))}
    </div>
  </div>
);
```

### 4.4 控制栏

```tsx
const ReplayControls = ({ player, speed, onSpeedChange }: Props) => (
  <div className="flex items-center justify-center gap-4 py-3">
    <Button variant="ghost" size="icon" onClick={player.reset}>
      <SkipBack className="h-4 w-4" />
    </Button>
    <Button size="icon" onClick={player.isPlaying ? player.pause : player.play}>
      {player.isPlaying
        ? <Pause className="h-5 w-5" />
        : <Play className="h-5 w-5" />}
    </Button>
    <Select value={String(speed)} onValueChange={v => onSpeedChange(Number(v))}>
      <SelectTrigger className="w-20">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="0.5">0.5x</SelectItem>
        <SelectItem value="1">1x</SelectItem>
        <SelectItem value="2">2x</SelectItem>
        <SelectItem value="4">4x</SelectItem>
      </SelectContent>
    </Select>
  </div>
);
```

## 5. 对局历史入口

在个人主页和排行榜中添加对局历史入口：

```tsx
// Profile 页面中
<Card>
  <CardHeader>
    <CardTitle className="text-base">对局历史</CardTitle>
  </CardHeader>
  <CardContent>
    {archives.map(archive => (
      <div key={archive.id} className="flex items-center justify-between py-2 border-b">
        <div>
          <span className="font-medium">{archive.gameId === 'werewolf' ? '狼人杀' : '谁是卧底'}</span>
          <span className="text-xs text-muted-foreground ml-2">
            {archive.playerCount}人 · {archive.totalRounds}轮 · {formatDate(archive.createdAt)}
          </span>
        </div>
        <Button size="sm" variant="ghost" onClick={() => navigate(`/replay/${archive.id}`)}>
          回放
        </Button>
      </div>
    ))}
  </CardContent>
</Card>
```

## 6. 路由

```
/replay/:archiveId — 回放播放器页面
/profile — 个人主页（含对局历史 tab）
```

## 7. 数据清理策略

- 对局存档永久保留
- 事件流数据保留 90 天（可配置），过期后删除 `game_events` 记录
- 精选对局（`featured=true`）永久保留事件流

```yaml
replay:
  event-retention-days: 90
  max-events-per-game: 500
```

## 8. 测试要点

- [ ] 事件记录完整性（所有关键操作都有对应事件）
- [ ] 上帝视角 vs 玩家视角的信息过滤
- [ ] 回放播放器的播放/暂停/快进/拖拽
- [ ] 不同速度下的播放流畅度
- [ ] 对局存档的分页查询
- [ ] 数据清理定时任务
