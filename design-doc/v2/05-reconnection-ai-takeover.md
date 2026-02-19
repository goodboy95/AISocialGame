# 模块 05：断线重连与 AI 托管

> 优先级：P0 | 阶段：第一阶段 | 依赖：模块 01（WebSocket） | 被依赖：无

## 1. 背景与目标

当前断线处理：玩家刷新页面后，通过 `localStorage` 中存储的 `room_player_{roomId}` 恢复 playerId，然后重新调用 `joinMutation`。但存在以下问题：

- 没有断线检测和重连提示 UI
- 刷新后自动重新 join 可能导致座位冲突
- 玩家长时间离线时游戏卡住（等待该玩家操作）
- 没有 AI 自动接管机制

目标：实现优雅的断线重连 + 超时 AI 托管，保证游戏流程不因单个玩家掉线而中断。

## 2. 整体流程

```
玩家在线
  │
  ├─► WebSocket 心跳正常
  │
  ├─► 心跳丢失（网络波动）
  │     │
  │     ├─► 前端：显示"连接中..."状态条
  │     ├─► 前端：stompjs 自动重连（3s 间隔）
  │     │
  │     ├─► 重连成功（< 30s）
  │     │     ├─► 全量同步游戏状态
  │     │     └─► 隐藏状态条，恢复正常
  │     │
  │     └─► 重连失败（> 30s）
  │           ├─► 前端：显示"连接已断开"+ 手动重连按钮
  │           └─► 后端：标记玩家为 DISCONNECTED
  │
  ├─► 玩家超时未操作（轮到该玩家但超过阈值）
  │     │
  │     ├─► 后端：AI 自动代替执行操作
  │     ├─► 后端：标记为 AI_TAKEOVER
  │     └─► 推送通知其他玩家："xxx 暂时离线，AI 代为操作"
  │
  └─► 玩家回归
        │
        ├─► 重新连接 WebSocket
        ├─► 恢复玩家控制权（取消 AI 托管）
        └─► 推送通知："xxx 已回归"
```

## 3. 后端设计

### 3.1 玩家连接状态

在 `GamePlayerState` 中新增连接状态：

```java
public class GamePlayerState {
    // ... 现有字段 ...

    private String connectionStatus;  // ONLINE | DISCONNECTED | AI_TAKEOVER
    private LocalDateTime lastActiveAt;
    private LocalDateTime disconnectedAt;
}
```

### 3.2 连接状态追踪服务

```java
@Service
public class PlayerConnectionService {

    private final ConcurrentHashMap<String, PlayerConnection> connections = new ConcurrentHashMap<>();

    public record PlayerConnection(
        String playerId,
        String roomId,
        String sessionId,
        LocalDateTime connectedAt,
        LocalDateTime lastHeartbeat
    ) {}

    // WebSocket 连接建立时调用
    public void onConnect(String playerId, String roomId, String sessionId) {
        connections.put(playerId, new PlayerConnection(
            playerId, roomId, sessionId, LocalDateTime.now(), LocalDateTime.now()));
    }

    // WebSocket 断开时调用
    public void onDisconnect(String sessionId) {
        connections.values().removeIf(c -> c.sessionId().equals(sessionId));
    }

    // 心跳更新
    public void heartbeat(String playerId) {
        PlayerConnection conn = connections.get(playerId);
        if (conn != null) {
            connections.put(playerId, new PlayerConnection(
                conn.playerId(), conn.roomId(), conn.sessionId(),
                conn.connectedAt(), LocalDateTime.now()));
        }
    }

    // 检查玩家是否在线
    public boolean isOnline(String playerId) {
        PlayerConnection conn = connections.get(playerId);
        if (conn == null) return false;
        return Duration.between(conn.lastHeartbeat(), LocalDateTime.now()).toSeconds() < 15;
    }
}
```

### 3.3 WebSocket 事件监听

```java
@Component
public class WebSocketEventListener {

    private final PlayerConnectionService connectionService;
    private final GamePushService pushService;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String playerId = accessor.getUser() != null ? accessor.getUser().getName() : null;
        String roomId = accessor.getFirstNativeHeader("X-Room-Id");
        if (playerId != null && roomId != null) {
            connectionService.onConnect(playerId, roomId, accessor.getSessionId());
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        connectionService.onDisconnect(event.getSessionId());
    }
}
```

### 3.4 AI 托管逻辑

在 `GamePlayService` 的超时处理中集成 AI 托管：

```java
/**
 * 检查当前需要操作的玩家是否超时，如果超时则 AI 代为操作
 */
private boolean checkAndTakeover(GameState state, Room room) {
    // 发言阶段：当前发言者超时
    if (isDiscussionPhase(state.getPhase())) {
        GamePlayerState speaker = currentSpeaker(state);
        if (speaker != null && !speaker.isAi()
            && !connectionService.isOnline(speaker.getPlayerId())
            && isOperationTimeout(state)) {

            markAiTakeover(state, speaker);
            String aiSpeech = aiDecisionService.generateSpeech(
                contextBuilder.build(state, room, speaker.getPlayerId()), null);
            addLog(state, "speak", speaker.getDisplayName() + "（托管）：" + aiSpeech);
            addSpeaker(state, speaker.getPlayerId());
            advanceToNextSpeaker(state, room);
            return true;
        }
    }

    // 投票阶段：未投票的离线玩家自动弃票
    if (isVotingPhase(state.getPhase())) {
        Map<String, String> votes = voteMap(state);
        boolean changed = false;
        for (GamePlayerState p : state.getPlayers()) {
            if (p.isAlive() && !p.isAi() && !votes.containsKey(p.getPlayerId())
                && !connectionService.isOnline(p.getPlayerId())) {
                votes.put(p.getPlayerId(), "abstain");
                addLog(state, "vote", p.getDisplayName() + " 离线，自动弃票");
                changed = true;
            }
        }
        if (changed) state.getData().put("votes", votes);
        return changed;
    }

    return false;
}

private void markAiTakeover(GameState state, GamePlayerState player) {
    player.setConnectionStatus("AI_TAKEOVER");
    addLog(state, "system", player.getDisplayName() + " 暂时离线，AI 代为操作");
    gamePushService.pushStateChange(state.getRoomId(),
        new GameStateEvent("PLAYER_TAKEOVER", state.getPhase(),
            state.getRoundNumber(), null,
            Map.of("playerId", player.getPlayerId(), "displayName", player.getDisplayName())));
}

private boolean isOperationTimeout(GameState state) {
    // 当前阶段已过去超过操作时限的 80%
    if (state.getPhaseEndsAt() == null) return false;
    long totalSeconds = /* 阶段总时长 */;
    long elapsed = Duration.between(state.getUpdatedAt(), LocalDateTime.now()).toSeconds();
    return elapsed > totalSeconds * 0.8;
}
```

### 3.5 玩家回归恢复

```java
// 在 state() 方法中检测玩家回归
public GameStateResponse state(String gameId, String roomId, User user, String playerIdHeader) {
    // ... 现有逻辑 ...

    String viewerId = resolvePlayerId(room, user, playerIdHeader);
    if (viewerId != null) {
        GamePlayerState player = playerById(state, viewerId);
        if (player != null && "AI_TAKEOVER".equals(player.getConnectionStatus())) {
            player.setConnectionStatus("ONLINE");
            player.setLastActiveAt(LocalDateTime.now());
            addLog(state, "system", player.getDisplayName() + " 已回归");
            gamePushService.pushStateChange(roomId,
                new GameStateEvent("PLAYER_RETURN", state.getPhase(),
                    state.getRoundNumber(), null,
                    Map.of("playerId", viewerId)));
            gameStateRepository.save(state);
        }
    }

    return buildResponse(gameId, room, state, viewerId);
}
```

## 4. 前端设计

### 4.1 连接状态指示条

```tsx
// components/game/ConnectionStatus.tsx
const ConnectionStatus = ({ connected }: { connected: boolean }) => {
  const [showReconnectButton, setShowReconnectButton] = useState(false);
  const disconnectTimeRef = useRef<number>(0);

  useEffect(() => {
    if (!connected) {
      disconnectTimeRef.current = Date.now();
      const timer = setTimeout(() => setShowReconnectButton(true), 30000);
      return () => clearTimeout(timer);
    } else {
      setShowReconnectButton(false);
      disconnectTimeRef.current = 0;
    }
  }, [connected]);

  if (connected) return null;

  return (
    <motion.div
      initial={{ y: -40 }}
      animate={{ y: 0 }}
      className="fixed top-0 left-0 right-0 z-50 bg-amber-500 text-white
        px-4 py-2 flex items-center justify-center gap-3 text-sm"
    >
      {!showReconnectButton ? (
        <>
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>连接中断，正在重连...</span>
        </>
      ) : (
        <>
          <WifiOff className="h-4 w-4" />
          <span>连接已断开</span>
          <Button size="sm" variant="secondary" onClick={handleManualReconnect}
            className="h-6 text-xs">
            手动重连
          </Button>
        </>
      )}
    </motion.div>
  );
};
```

### 4.2 重连后状态同步

```typescript
// hooks/useGameSocket.ts 中的重连处理
onConnect: () => {
  setConnected(true);

  // 重连后全量同步
  if (wasDisconnected.current) {
    queryClient.invalidateQueries({ queryKey: ['game-state', roomId] });
    wasDisconnected.current = false;
    toast.success('已重新连接');
  }

  // 重新订阅
  subscribeToTopics();
},
onDisconnect: () => {
  setConnected(false);
  wasDisconnected.current = true;
},
```

### 4.3 托管状态显示

在玩家列表中显示托管状态：

```tsx
// 玩家卡片中
{player.connectionStatus === "AI_TAKEOVER" && (
  <Badge variant="outline" className="text-amber-600 border-amber-200 bg-amber-50 text-[10px]">
    <Bot className="h-3 w-3 mr-0.5" /> 托管中
  </Badge>
)}
{player.connectionStatus === "DISCONNECTED" && (
  <Badge variant="outline" className="text-slate-400 border-slate-200 text-[10px]">
    <WifiOff className="h-3 w-3 mr-0.5" /> 离线
  </Badge>
)}
```

## 5. 数据库变更

```sql
-- game_states.players JSON 中的 GamePlayerState 新增字段（无需 DDL，JSON 字段自动扩展）
-- connectionStatus: "ONLINE" | "DISCONNECTED" | "AI_TAKEOVER"
-- lastActiveAt: timestamp
-- disconnectedAt: timestamp
```

## 6. 配置项

```yaml
# application.yml
connection:
  heartbeat-interval-seconds: 10
  disconnect-threshold-seconds: 15
  takeover-timeout-ratio: 0.8    # 阶段时间过去 80% 后触发托管
  max-reconnect-wait-seconds: 60 # 超过此时间标记为 DISCONNECTED
```

## 7. 测试要点

- [ ] 网络断开后前端显示重连状态条
- [ ] stompjs 自动重连成功后状态同步正确
- [ ] 超过 30s 未重连显示手动重连按钮
- [ ] 玩家离线后 AI 正确接管操作
- [ ] 玩家回归后恢复控制权
- [ ] 多个玩家同时断线的处理
- [ ] 房主断线时的特殊处理（是否转移房主权限）
- [ ] 托管状态在其他玩家界面正确显示
