# 模块 11：GameEngine 抽象层

> 优先级：P2 | 阶段：第三阶段 | 依赖：模块 02（AI 决策）、模块 12（投票/计时器） | 被依赖：所有未来新游戏

## 1. 背景与目标

当前 `GamePlayService` 通过 `if (gameId.equals("undercover"))` / `if (gameId.equals("werewolf"))` 分支处理不同游戏逻辑，所有游戏的代码耦合在一个 1055 行的类中。添加新游戏需要在多处插入新的 if-else 分支，维护成本高且容易出错。

目标：抽象出通用的游戏引擎接口，每个游戏类型实现自己的引擎，通过注册机制自动发现。新增游戏只需实现接口 + 注册，无需修改框架代码。

## 2. 核心接口设计

### 2.1 游戏引擎接口

```java
/**
 * 游戏引擎接口 — 每种游戏类型实现一个
 */
public interface GameEngine {

    /** 游戏类型标识，如 "undercover"、"werewolf" */
    String getGameId();

    /** 游戏元数据 */
    GameMetadata getMetadata();

    /** 初始化并开始游戏 */
    GameState start(Room room, String initiatorId);

    /** 处理玩家行动（发言、投票、夜晚操作等） */
    GameState handleAction(GameState state, Room room, PlayerAction action);

    /** 获取当前状态的视图（按玩家视角过滤信息） */
    GameStateResponse buildView(GameState state, Room room, String viewerId);

    /** 检查并推进自动流程（AI 行动、超时处理等） */
    GameState autoAdvance(GameState state, Room room);

    /** 验证房间配置是否满足开局条件 */
    ValidationResult validateStart(Room room);

    /** 获取该游戏支持的阶段配置（用于前端渲染） */
    List<PhaseDefinition> getPhaseDefinitions();

    /** 获取该游戏支持的角色列表 */
    List<RoleDefinition> getRoleDefinitions();
}
```

### 2.2 支撑数据结构

```java
public record GameMetadata(
    String gameId,
    String name,
    String description,
    int minPlayers,
    int maxPlayers,
    List<String> tags,
    String iconName
) {}

public record PlayerAction(
    String type,          // "SPEAK" | "VOTE" | "NIGHT_ACTION" | "READY" | "SKIP"
    String actorId,
    String targetId,
    String content,
    Map<String, Object> extra
) {}

public record ValidationResult(
    boolean valid,
    String message
) {}

public record PhaseDefinition(
    String phase,
    String displayName,
    String icon,
    int defaultDurationSeconds,
    boolean allowChat,
    String bgGradient
) {}

public record RoleDefinition(
    String role,
    String displayName,
    String description,
    String faction,       // "GOOD" | "EVIL" | "NEUTRAL"
    boolean hasNightAction
) {}
```

## 3. 引擎注册表

```java
@Component
public class GameEngineRegistry {

    private final Map<String, GameEngine> engines = new HashMap<>();

    @Autowired
    public GameEngineRegistry(List<GameEngine> engineList) {
        for (GameEngine engine : engineList) {
            engines.put(engine.getGameId(), engine);
        }
    }

    public GameEngine getEngine(String gameId) {
        GameEngine engine = engines.get(gameId);
        if (engine == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的游戏类型: " + gameId);
        }
        return engine;
    }

    public List<GameMetadata> listGames() {
        return engines.values().stream()
            .map(GameEngine::getMetadata)
            .toList();
    }

    public boolean supports(String gameId) {
        return engines.containsKey(gameId);
    }
}
```

## 4. 重构后的 GamePlayService

```java
@Service
@Transactional
public class GamePlayService {

    private final GameEngineRegistry registry;
    private final GameStateRepository gameStateRepository;
    private final RoomService roomService;
    private final GamePushService pushService;
    private final GameEventRecorder eventRecorder;

    public GameStateResponse state(String gameId, String roomId, User user, String playerIdHeader) {
        GameEngine engine = registry.getEngine(gameId);
        Room room = roomService.getRoom(roomId);
        String viewerId = resolvePlayerId(room, user, playerIdHeader);

        Optional<GameState> optState = gameStateRepository.findById(roomId);
        if (optState.isEmpty()) {
            return engine.buildView(null, room, viewerId);
        }

        GameState state = optState.get();
        // 自动推进（AI 行动、超时等）
        GameState advanced = engine.autoAdvance(state, room);
        if (advanced != state) {
            advanced = gameStateRepository.save(advanced);
        }
        return engine.buildView(advanced, room, viewerId);
    }

    public GameStateResponse start(String gameId, String roomId, User user, String playerIdHeader) {
        GameEngine engine = registry.getEngine(gameId);
        Room room = roomService.getRoom(roomId);
        String actorId = resolvePlayerId(room, user, playerIdHeader);

        ValidationResult validation = engine.validateStart(room);
        if (!validation.valid()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, validation.message());
        }

        GameState state = engine.start(room, actorId);
        state = gameStateRepository.save(state);
        roomService.updateStatus(roomId, RoomStatus.PLAYING);
        pushService.pushStateChange(roomId,
            new GameStateEvent("GAME_START", state.getPhase(), 0, null, null));
        return engine.buildView(state, room, actorId);
    }

    public GameStateResponse handleAction(String gameId, String roomId,
            PlayerAction action, User user, String playerIdHeader) {
        GameEngine engine = registry.getEngine(gameId);
        Room room = roomService.getRoom(roomId);
        action = action.withActorId(resolvePlayerId(room, user, playerIdHeader));

        GameState state = gameStateRepository.findById(roomId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "游戏尚未开始"));

        GameState newState = engine.handleAction(state, room, action);
        newState = gameStateRepository.save(newState);

        pushService.pushStateChange(roomId,
            new GameStateEvent(action.type(), newState.getPhase(),
                newState.getRoundNumber(), newState.getCurrentSeat(), null));

        return engine.buildView(newState, room, action.actorId());
    }
}
```

## 5. 谁是卧底引擎实现（示例）

```java
@Component
public class UndercoverEngine implements GameEngine {

    private final UndercoverWordRepository wordRepository;
    private final AiDecisionService aiDecisionService;

    @Override
    public String getGameId() { return "undercover"; }

    @Override
    public GameMetadata getMetadata() {
        return new GameMetadata("undercover", "谁是卧底",
            "每人获得一个词语，通过描述找出拿到不同词的卧底",
            4, 12, List.of("推理", "语言", "社交"), "Eye");
    }

    @Override
    public GameState start(Room room, String initiatorId) {
        // 从现有 startUndercover() 逻辑迁移
        // ...
    }

    @Override
    public GameState handleAction(GameState state, Room room, PlayerAction action) {
        return switch (action.type()) {
            case "SPEAK" -> handleSpeak(state, room, action);
            case "VOTE" -> handleVote(state, room, action);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的操作");
        };
    }

    @Override
    public GameState autoAdvance(GameState state, Room room) {
        // 从现有 autoAdvanceUndercover() 逻辑迁移
        // ...
    }

    @Override
    public ValidationResult validateStart(Room room) {
        if (room.getSeats().size() < 4) {
            return new ValidationResult(false, "至少需要4名玩家");
        }
        return new ValidationResult(true, null);
    }

    @Override
    public List<PhaseDefinition> getPhaseDefinitions() {
        return List.of(
            new PhaseDefinition("DESCRIPTION", "描述阶段", "💬", 60, true, "from-blue-600 to-blue-800"),
            new PhaseDefinition("VOTING", "投票阶段", "🗳️", 30, true, "from-amber-600 to-red-700"),
            new PhaseDefinition("SETTLEMENT", "游戏结束", "🎭", 0, true, "from-purple-600 to-purple-900")
        );
    }

    @Override
    public List<RoleDefinition> getRoleDefinitions() {
        return List.of(
            new RoleDefinition("CIVILIAN", "平民", "拿到多数词的玩家", "GOOD", false),
            new RoleDefinition("UNDERCOVER", "卧底", "拿到少数词的玩家", "EVIL", false),
            new RoleDefinition("BLANK", "白板", "没有词语的玩家", "NEUTRAL", false)
        );
    }
}
```

## 6. Controller 简化

```java
@RestController
@RequestMapping("/api/games/{gameId}/rooms/{roomId}")
public class GamePlayController {

    private final GamePlayService gamePlayService;

    @GetMapping("/state")
    public GameStateResponse state(@PathVariable String gameId, @PathVariable String roomId, ...) {
        return gamePlayService.state(gameId, roomId, user, playerIdHeader);
    }

    @PostMapping("/start")
    public GameStateResponse start(@PathVariable String gameId, @PathVariable String roomId, ...) {
        return gamePlayService.start(gameId, roomId, user, playerIdHeader);
    }

    // 统一的行动接口，替代原来的 speak/vote/nightAction 三个接口
    @PostMapping("/action")
    public GameStateResponse action(@PathVariable String gameId, @PathVariable String roomId,
            @RequestBody PlayerAction action, ...) {
        return gamePlayService.handleAction(gameId, roomId, action, user, playerIdHeader);
    }

    // 保留旧接口做兼容（内部转发到 action）
    @PostMapping("/speak")
    public GameStateResponse speak(...) {
        return action(gameId, roomId, new PlayerAction("SPEAK", null, null, request.getContent(), null), ...);
    }

    @PostMapping("/vote")
    public GameStateResponse vote(...) {
        return action(gameId, roomId, new PlayerAction("VOTE", null, request.getTargetPlayerId(), null,
            Map.of("abstain", request.isAbstain())), ...);
    }
}
```

## 7. 前端适配

### 7.1 动态游戏组件加载

```typescript
// 游戏组件注册表
const gameComponents: Record<string, React.LazyExoticComponent<any>> = {
  undercover: lazy(() => import('./games/UndercoverRoom')),
  werewolf: lazy(() => import('./games/WerewolfRoom')),
  // 未来新游戏只需在此注册
};

// Lobby.tsx 中
const GameComponent = gameComponents[gameId];
if (GameComponent) {
  return (
    <Suspense fallback={<div>加载中...</div>}>
      <GameComponent />
    </Suspense>
  );
}
```

### 7.2 通用游戏 Hook

```typescript
// hooks/useGameEngine.ts
export function useGameEngine(gameId: string, roomId: string) {
  const state = useQuery<GameState>({ ... });

  const doAction = useMutation({
    mutationFn: (action: PlayerAction) =>
      api.post(`/api/games/${gameId}/rooms/${roomId}/action`, action),
  });

  const speak = (content: string) => doAction.mutate({ type: "SPEAK", content });
  const vote = (targetId: string, abstain = false) =>
    doAction.mutate({ type: "VOTE", targetId, extra: { abstain } });
  const nightAction = (action: string, targetId?: string) =>
    doAction.mutate({ type: "NIGHT_ACTION", targetId, extra: { action } });

  return { state, speak, vote, nightAction, doAction };
}
```

## 8. 新游戏接入清单

添加一个新游戏类型需要：

1. 后端：实现 `GameEngine` 接口（一个 Java 类），Spring 自动注册
2. 后端：在 `prompt.yml` 中添加 AI 决策 prompt 模板
3. 前端：创建游戏房间组件 `pages/games/XxxRoom.tsx`
4. 前端：在 `gameComponents` 注册表中注册
5. 数据库：在 `games` 表中插入游戏元数据记录
6. 可选：在 `achievement_definitions` 中添加游戏专属成就

无需修改 `GamePlayService`、`GamePlayController`、`Lobby.tsx` 等框架代码。

## 9. 迁移策略

1. 先创建接口和注册表
2. 将现有 `GamePlayService` 中的卧底逻辑提取到 `UndercoverEngine`
3. 将狼人杀逻辑提取到 `WerewolfEngine`
4. `GamePlayService` 改为委托给 `GameEngineRegistry`
5. 保留旧 API 接口做兼容，新增统一的 `/action` 接口
6. 前端逐步迁移到 `useGameEngine` hook

## 10. 测试要点

- [ ] 引擎注册与发现
- [ ] 两个现有游戏迁移后功能不变
- [ ] 统一 `/action` 接口正确路由到对应引擎
- [ ] 旧接口兼容性
- [ ] 新游戏引擎的热插拔（添加新 @Component 即可）
- [ ] `getPhaseDefinitions` 和 `getRoleDefinitions` 正确返回
