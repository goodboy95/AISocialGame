# 模块 12：通用投票引擎与阶段计时器

> 优先级：P2 | 阶段：第三阶段 | 依赖：模块 01（WebSocket） | 被依赖：模块 11（GameEngine 抽象层）

## 1. 背景与目标

当前投票逻辑分散在 `voteUndercover`、`voteWerewolf`、`resolveUndercoverVoting`、`resolveWerewolfVoting` 等方法中，存在大量重复代码（收集投票、计票、平票处理）。阶段计时也是各处硬编码。

目标：抽取通用的投票引擎和阶段计时器，作为 GameEngine 的基础设施，任何回合制游戏都可以直接使用。

## 2. 投票引擎

### 2.1 接口设计

```java
/**
 * 通用投票引擎 — 处理收集投票、计票、平票等通用逻辑
 */
@Component
public class VotingEngine {

    /**
     * 投票配置
     */
    public record VotingConfig(
        boolean allowAbstain,       // 是否允许弃票
        boolean anonymousVote,      // 是否匿名投票（结果不公开谁投了谁）
        TieBreaker tieBreaker,      // 平票处理策略
        boolean selfVoteAllowed,    // 是否允许投自己
        int maxVotesPerPlayer       // 每人票数（通常为 1）
    ) {
        public static VotingConfig standard() {
            return new VotingConfig(true, false, TieBreaker.NO_ELIMINATION, false, 1);
        }
    }

    public enum TieBreaker {
        NO_ELIMINATION,    // 平票无人出局
        RANDOM_ELIMINATE,  // 平票随机淘汰一人
        REVOTE,            // 平票重新投票（仅限平票者之间）
        ALL_ELIMINATE      // 平票全部淘汰
    }

    /**
     * 投票结果
     */
    public record VoteResult(
        boolean resolved,                    // 是否已结算
        String eliminatedPlayerId,           // 被淘汰的玩家（null 表示无人淘汰）
        String eliminatedPlayerName,
        Map<String, Long> voteCounts,        // 每个目标的得票数
        Map<String, String> voteDetails,     // 谁投了谁（非匿名时）
        boolean isTie,                       // 是否平票
        String message                       // 结果描述
    ) {}

    /**
     * 提交一票
     */
    public void castVote(GameState state, String voterId, String targetId,
                         boolean abstain, VotingConfig config) {
        Map<String, String> votes = getVoteMap(state);

        if (votes.containsKey(voterId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "已完成投票");
        }
        if (!abstain && !config.selfVoteAllowed() && voterId.equals(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能投自己");
        }
        if (abstain && !config.allowAbstain()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "本局不允许弃票");
        }

        votes.put(voterId, abstain ? "abstain" : targetId);
        state.getData().put("votes", votes);
    }

    /**
     * 检查是否所有存活玩家都已投票
     */
    public boolean allVoted(GameState state) {
        Map<String, String> votes = getVoteMap(state);
        long aliveCount = state.getPlayers().stream().filter(GamePlayerState::isAlive).count();
        return votes.size() >= aliveCount;
    }

    /**
     * 结算投票
     */
    public VoteResult resolve(GameState state, VotingConfig config, boolean forceTimeout) {
        Map<String, String> votes = getVoteMap(state);
        long aliveCount = state.getPlayers().stream().filter(GamePlayerState::isAlive).count();

        if (!forceTimeout && votes.size() < aliveCount) {
            return new VoteResult(false, null, null, Map.of(), votes, false, "投票进行中");
        }

        // 计票（排除弃票）
        Map<String, Long> counts = votes.values().stream()
            .filter(v -> !"abstain".equals(v))
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        if (counts.isEmpty()) {
            return new VoteResult(true, null, null, counts, votes, false, "全员弃票，无人出局");
        }

        long maxVotes = counts.values().stream().max(Long::compareTo).orElse(0L);
        List<String> topCandidates = counts.entrySet().stream()
            .filter(e -> e.getValue() == maxVotes)
            .map(Map.Entry::getKey)
            .toList();

        // 平票处理
        if (topCandidates.size() > 1) {
            return handleTie(state, topCandidates, counts, votes, config);
        }

        // 唯一最高票
        String eliminatedId = topCandidates.get(0);
        GamePlayerState eliminated = findPlayer(state, eliminatedId);
        String name = eliminated != null ? eliminated.getDisplayName() : "未知";

        return new VoteResult(true, eliminatedId, name, counts, votes, false,
            name + " 以 " + maxVotes + " 票被淘汰");
    }

    private VoteResult handleTie(GameState state, List<String> candidates,
            Map<String, Long> counts, Map<String, String> votes, VotingConfig config) {
        return switch (config.tieBreaker()) {
            case NO_ELIMINATION -> new VoteResult(true, null, null, counts, votes, true, "平票，无人出局");
            case RANDOM_ELIMINATE -> {
                String randomId = candidates.get(new Random().nextInt(candidates.size()));
                GamePlayerState p = findPlayer(state, randomId);
                yield new VoteResult(true, randomId, p != null ? p.getDisplayName() : "", counts, votes, true,
                    "平票，随机淘汰 " + (p != null ? p.getDisplayName() : ""));
            }
            case ALL_ELIMINATE -> new VoteResult(true, null, null, counts, votes, true, "平票，全部淘汰");
            case REVOTE -> new VoteResult(false, null, null, counts, votes, true, "平票，进入重新投票");
        };
    }

    /**
     * AI 自动投票
     */
    public boolean fillAiVotes(GameState state, AiDecisionService aiService, Room room) {
        Map<String, String> votes = getVoteMap(state);
        boolean changed = false;
        for (GamePlayerState player : state.getPlayers()) {
            if (!player.isAi() || !player.isAlive() || votes.containsKey(player.getPlayerId())) continue;
            String targetId = aiService.decideVote(
                contextBuilder.build(state, room, player.getPlayerId()),
                personaService.findById(player.getPersonaId()));
            votes.put(player.getPlayerId(), targetId != null ? targetId : "abstain");
            changed = true;
        }
        if (changed) state.getData().put("votes", votes);
        return changed;
    }

    /**
     * 重置投票数据（新一轮）
     */
    public void resetVotes(GameState state) {
        state.getData().put("votes", new HashMap<String, String>());
    }
}
```

## 3. 阶段计时器

### 3.1 接口设计

```java
/**
 * 通用阶段计时器 — 管理阶段时限和超时回调
 */
@Component
public class PhaseTimer {

    /**
     * 阶段配置
     */
    public record PhaseTimerConfig(
        String phase,
        int durationSeconds,
        Runnable onTimeout       // 超时回调
    ) {}

    /**
     * 设置阶段计时
     */
    public void startPhase(GameState state, String phase, int durationSeconds) {
        state.setPhase(phase);
        state.setPhaseEndsAt(LocalDateTime.now().plusSeconds(durationSeconds));
    }

    /**
     * 检查是否超时
     */
    public boolean isExpired(GameState state) {
        if (state.getPhaseEndsAt() == null) return false;
        return LocalDateTime.now().isAfter(state.getPhaseEndsAt());
    }

    /**
     * 获取剩余秒数
     */
    public long remainingSeconds(GameState state) {
        if (state.getPhaseEndsAt() == null) return 0;
        return Math.max(0, Duration.between(LocalDateTime.now(), state.getPhaseEndsAt()).toSeconds());
    }

    /**
     * 清除计时（如结算阶段）
     */
    public void clearTimer(GameState state) {
        state.setPhaseEndsAt(null);
    }

    /**
     * 从房间配置中读取阶段时长
     */
    public int resolveDuration(Room room, String configKey, int defaultValue) {
        Object value = room.getConfig().get(configKey);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
```

### 3.2 后端定时检查

```java
/**
 * 定时检查所有进行中的游戏，处理超时
 */
@Component
public class PhaseTimeoutScheduler {

    private final GameStateRepository gameStateRepository;
    private final GameEngineRegistry engineRegistry;
    private final RoomService roomService;

    @Scheduled(fixedRate = 3000) // 每 3 秒检查一次
    public void checkTimeouts() {
        List<GameState> activeGames = gameStateRepository.findByPhaseNot("WAITING");
        for (GameState state : activeGames) {
            if (state.getPhaseEndsAt() != null
                && LocalDateTime.now().isAfter(state.getPhaseEndsAt())) {
                try {
                    GameEngine engine = engineRegistry.getEngine(state.getGameId());
                    Room room = roomService.getRoom(state.getRoomId());
                    GameState advanced = engine.autoAdvance(state, room);
                    if (advanced != state) {
                        gameStateRepository.save(advanced);
                    }
                } catch (Exception e) {
                    log.error("超时处理失败: room={}", state.getRoomId(), e);
                }
            }
        }
    }
}
```

## 4. 在 GameEngine 中使用

```java
@Component
public class UndercoverEngine implements GameEngine {

    private final VotingEngine votingEngine;
    private final PhaseTimer phaseTimer;

    @Override
    public GameState handleAction(GameState state, Room room, PlayerAction action) {
        return switch (action.type()) {
            case "SPEAK" -> handleSpeak(state, room, action);
            case "VOTE" -> {
                votingEngine.castVote(state, action.actorId(), action.targetId(),
                    Boolean.TRUE.equals(action.extra().get("abstain")),
                    VotingConfig.standard());

                // 检查是否所有人都投了
                if (votingEngine.allVoted(state)) {
                    VoteResult result = votingEngine.resolve(state, VotingConfig.standard(), false);
                    if (result.resolved()) {
                        applyVoteResult(state, result);
                    }
                }
                yield state;
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的操作");
        };
    }

    private void transitionToVoting(GameState state, Room room) {
        phaseTimer.startPhase(state, "VOTING",
            phaseTimer.resolveDuration(room, "voteTime", 30));
        votingEngine.resetVotes(state);
    }
}
```

## 5. 前端通用投票组件

```tsx
// components/game/VotingPanel.tsx
interface VotingPanelProps {
  candidates: { id: string; name: string; avatar?: string }[];
  allowAbstain: boolean;
  hasVoted: boolean;
  onVote: (targetId: string, abstain: boolean) => void;
  voteResult?: VoteResult;  // 投票结束后显示结果
  isLoading: boolean;
}

const VotingPanel = ({ candidates, allowAbstain, hasVoted, onVote, voteResult, isLoading }: VotingPanelProps) => {
  const [selected, setSelected] = useState<string | null>(null);

  if (voteResult) {
    return <VoteResultDisplay result={voteResult} />;
  }

  if (hasVoted) {
    return <div className="text-sm text-muted-foreground text-center py-4">已投票，等待其他玩家...</div>;
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-2">
        {candidates.map(c => (
          <Button key={c.id} variant={selected === c.id ? "default" : "outline"}
            className="justify-start" onClick={() => setSelected(c.id)}>
            <Avatar className="h-6 w-6 mr-2">
              <AvatarImage src={c.avatar} />
              <AvatarFallback>{c.name[0]}</AvatarFallback>
            </Avatar>
            {c.name}
          </Button>
        ))}
      </div>
      <div className="flex gap-2">
        <Button className="flex-1" disabled={!selected || isLoading}
          onClick={() => selected && onVote(selected, false)}>
          投票
        </Button>
        {allowAbstain && (
          <Button variant="ghost" disabled={isLoading}
            onClick={() => onVote("", true)}>
            弃票
          </Button>
        )}
      </div>
    </div>
  );
};
```

### 投票结果展示

```tsx
const VoteResultDisplay = ({ result }: { result: VoteResult }) => (
  <div className="space-y-3">
    <div className="text-center font-medium">{result.message}</div>
    {/* 票数分布柱状图 */}
    <div className="space-y-2">
      {Object.entries(result.voteCounts)
        .sort(([, a], [, b]) => Number(b) - Number(a))
        .map(([playerId, count]) => (
          <div key={playerId} className="flex items-center gap-2">
            <span className="text-sm w-20 truncate">{getPlayerName(playerId)}</span>
            <div className="flex-1 h-4 bg-slate-100 rounded-full overflow-hidden">
              <div className="h-full bg-blue-500 rounded-full transition-all"
                style={{ width: `${(Number(count) / maxVotes) * 100}%` }} />
            </div>
            <span className="text-sm font-medium w-8 text-right">{String(count)}</span>
          </div>
        ))}
    </div>
  </div>
);
```

## 6. 数据库变更

无。投票数据存储在 `game_states.data` JSON 字段中，与现有方式一致。

## 7. 测试要点

- [ ] 投票收集：正常投票、弃票、重复投票拦截
- [ ] 计票：单一最高票、平票各策略
- [ ] AI 自动投票集成
- [ ] 阶段计时：设置、检查超时、清除
- [ ] 超时调度器正确触发
- [ ] 通用投票组件在两个游戏中的表现
- [ ] 投票结果可视化
