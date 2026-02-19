# 模块 02：AI 决策框架

> 优先级：P0 | 阶段：第一阶段 | 依赖：无 | 被依赖：模块 11（GameEngine 抽象层）

## 1. 背景与目标

当前 AI 行为存在两个核心问题：

1. **发言缺乏上下文** — `AiGameSpeechService.buildDescription(word)` 只传入词语本身，AI 无法参考其他玩家的发言来调整策略
2. **投票完全随机** — `fillAiVotes()` 使用 `random.nextInt(targets.size())` 选择目标，没有任何推理

目标：构建一个通用的 AI 决策框架，让 AI 玩家能够：
- 基于完整游戏上下文生成有逻辑的发言
- 基于发言分析和游戏局势做出合理的投票决策
- 不同 Persona 表现出不同的性格和策略风格
- 框架可复用于未来新增的任何游戏类型

## 2. 架构设计

```
┌─────────────────────────────────────────────────┐
│                 GamePlayService                  │
│  (调用 AI 决策时不关心具体实现)                    │
└──────────────┬──────────────────────────────────┘
               │ 调用
               ▼
┌─────────────────────────────────────────────────┐
│            AiDecisionService                     │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌───────────────┐ │
│  │ContextBuilder│PromptBuilder│ DecisionParser│ │
│  │ 构建游戏上下文│ 组装 Prompt │ 解析 AI 输出  │ │
│  └──────────┘  └──────────┘  └───────────────┘ │
└──────────────┬──────────────────────────────────┘
               │ gRPC
               ▼
┌─────────────────────────────────────────────────┐
│            ai-service (AIENIE)                   │
│            LLM 推理                              │
└─────────────────────────────────────────────────┘
```

### 2.1 核心接口

```java
public interface AiDecisionService {

    /**
     * AI 生成发言内容
     * @param context 完整游戏上下文
     * @param persona AI 人设
     * @return 发言文本
     */
    String generateSpeech(GameContext context, Persona persona);

    /**
     * AI 做出投票决策
     * @param context 完整游戏上下文
     * @param persona AI 人设
     * @return 目标玩家 ID
     */
    String decideVote(GameContext context, Persona persona);

    /**
     * AI 做出夜晚行动决策（狼人杀专用，但接口通用）
     * @param context 完整游戏上下文
     * @param persona AI 人设
     * @return 行动决策
     */
    NightDecision decideNightAction(GameContext context, Persona persona);
}
```

## 3. 游戏上下文模型

```java
/**
 * 传递给 AI 的完整游戏上下文，与具体游戏类型无关
 */
public record GameContext(
    // 基本信息
    String gameType,            // "undercover" | "werewolf"
    int round,                  // 当前轮次
    String phase,               // 当前阶段

    // 自身信息
    String myPlayerId,
    String myRole,              // "WEREWOLF" | "SEER" | "CIVILIAN" | "UNDERCOVER" 等
    String myWord,              // 谁是卧底的词语（如适用）
    int mySeatNumber,

    // 所有玩家信息（AI 可见的部分）
    List<PlayerInfo> players,

    // 历史发言记录
    List<SpeechRecord> speechHistory,

    // 历史投票记录
    List<VoteRecord> voteHistory,

    // 游戏特定数据
    Map<String, Object> extraData  // 如预言家查验结果、女巫药品状态等
) {}

public record PlayerInfo(
    String playerId,
    String displayName,
    int seatNumber,
    boolean alive,
    boolean isAi,
    String knownRole           // 已知身份（如已出局被揭示的）
) {}

public record SpeechRecord(
    int round,
    String playerId,
    String displayName,
    String content,
    long timestamp
) {}

public record VoteRecord(
    int round,
    String voterId,
    String voterName,
    String targetId,
    String targetName,
    boolean abstain
) {}
```

## 4. Prompt 工程

### 4.1 Prompt 模板结构

所有 prompt 模板存放在 `prompt.yml` 中，按游戏类型和决策类型组织：

```yaml
ai-decision:
  undercover:
    speech: |
      你正在玩"谁是卧底"游戏。
      你的身份：{role}
      你的词语：{word}
      当前是第 {round} 轮描述阶段。

      场上玩家：
      {playerList}

      前面玩家的描述：
      {speechHistory}

      {personaInstruction}

      请用一句话描述你的词语（15-30字），注意：
      - 不要直接说出词语本身
      - 如果你是卧底，要尽量模仿平民的描述风格，不要暴露差异
      - 如果你是平民，描述要有辨识度但不能太直白
      - 参考前面玩家的描述来调整你的策略

      只输出描述内容，不要输出其他任何文字。

    vote: |
      你正在玩"谁是卧底"游戏。
      你的身份：{role}
      你的词语：{word}
      当前是第 {round} 轮投票阶段。

      场上存活玩家：
      {alivePlayerList}

      本轮所有描述：
      {currentRoundSpeeches}

      历史投票记录：
      {voteHistory}

      {personaInstruction}

      请分析每位玩家的描述，判断谁最可能是卧底。
      输出格式：VOTE:{seatNumber}
      只输出投票指令，不要输出分析过程。

  werewolf:
    day-speech: |
      你正在玩"狼人杀"游戏。
      你的身份：{role}
      当前是第 {round} 天的讨论阶段。

      场上玩家：
      {playerList}

      昨晚事件：
      {lastNightEvents}

      今天已有的发言：
      {todaySpeeches}

      历史投票记录：
      {voteHistory}

      {roleSpecificInfo}
      {personaInstruction}

      请发表你的看法（30-80字），注意：
      - 如果你是狼人，要伪装成好人，可以适当甩锅或跟票
      - 如果你是好人，要基于已知信息做推理
      - 发言要有逻辑性，引用具体的事件或其他玩家的行为作为依据

      只输出发言内容。

    vote: |
      你正在玩"狼人杀"游戏。
      你的身份：{role}
      当前是第 {round} 天的投票阶段。

      场上存活玩家：
      {alivePlayerList}

      今天的讨论内容：
      {todaySpeeches}

      {roleSpecificInfo}
      {personaInstruction}

      请决定投票目标。
      输出格式：VOTE:{seatNumber}

    night-action: |
      你正在玩"狼人杀"游戏。
      你的身份：{role}
      当前是第 {round} 夜。

      场上存活玩家：
      {alivePlayerList}

      白天讨论和投票情况：
      {dayEvents}

      {roleSpecificInfo}
      {personaInstruction}

      请选择你的夜晚行动目标。
      输出格式：TARGET:{seatNumber}
```

### 4.2 Persona 指令注入

每个 AI 人设（Persona）的性格特征会被注入到 `{personaInstruction}` 占位符中：

```java
private String buildPersonaInstruction(Persona persona) {
    if (persona == null) return "";
    return String.format(
        "你的性格设定：%s。说话风格：%s。策略倾向：%s。",
        persona.getTrait(),
        persona.getSpeechStyle(),   // 新增字段
        persona.getStrategyStyle()  // 新增字段
    );
}
```

Persona 模型扩展：

```java
// Persona 实体新增字段
private String speechStyle;     // "简洁直接" | "委婉含蓄" | "情绪化" | "逻辑严密"
private String strategyStyle;   // "激进型" | "保守型" | "跟风型" | "独立思考型"
private String difficultyLevel; // "EASY" | "NORMAL" | "HARD" | "EXPERT"
```

### 4.3 难度分级策略

| 难度 | 发言策略 | 投票策略 | 夜晚策略 |
|------|----------|----------|----------|
| EASY | 描述较直白，容易被识破 | 20% 概率投错人 | 随机选目标 |
| NORMAL | 正常描述，参考上下文 | 基于发言分析投票 | 基于局势选目标 |
| HARD | 精心伪装，模仿他人风格 | 综合分析 + 历史数据 | 优先击杀威胁最大的 |
| EXPERT | 高级策略（反串、做局） | 博弈论最优策略 | 多维度综合决策 |

难度通过调整 prompt 中的指令实现：

```java
private String getDifficultyInstruction(String level) {
    return switch (level) {
        case "EASY" -> "你不太擅长这个游戏，描述时可能会不小心暴露关键信息，投票时容易被别人的发言误导。";
        case "HARD" -> "你是一个经验丰富的玩家，善于隐藏身份和分析他人。投票时会综合考虑所有线索。";
        case "EXPERT" -> "你是顶级玩家，擅长高级策略如反串、做局、引导舆论。你的每一步都经过深思熟虑。";
        default -> "你是一个普通玩家，会根据场上信息做出合理判断。";
    };
}
```

## 5. 决策解析

AI 返回的文本需要解析为结构化决策：

```java
@Component
public class AiDecisionParser {

    /**
     * 从 AI 输出中解析投票目标
     * 支持格式：VOTE:3 / 投票给3号 / 座位3
     */
    public Optional<Integer> parseVoteTarget(String aiOutput, List<PlayerInfo> alivePlayers) {
        // 1. 尝试匹配 VOTE:{number} 格式
        Matcher matcher = Pattern.compile("VOTE:(\\d+)").matcher(aiOutput);
        if (matcher.find()) {
            int seat = Integer.parseInt(matcher.group(1));
            if (isValidSeat(seat, alivePlayers)) return Optional.of(seat);
        }

        // 2. 尝试匹配中文数字
        matcher = Pattern.compile("(\\d+)\\s*号").matcher(aiOutput);
        if (matcher.find()) {
            int seat = Integer.parseInt(matcher.group(1));
            if (isValidSeat(seat, alivePlayers)) return Optional.of(seat);
        }

        // 3. 兜底：随机选择（记录日志以便调优）
        log.warn("AI 投票输出无法解析: {}", aiOutput);
        return Optional.empty();
    }

    /**
     * 从 AI 输出中解析夜晚行动目标
     */
    public Optional<Integer> parseNightTarget(String aiOutput, List<PlayerInfo> alivePlayers) {
        Matcher matcher = Pattern.compile("TARGET:(\\d+)").matcher(aiOutput);
        if (matcher.find()) {
            int seat = Integer.parseInt(matcher.group(1));
            if (isValidSeat(seat, alivePlayers)) return Optional.of(seat);
        }
        return Optional.empty();
    }

    /**
     * 清理发言输出（去除格式标记、限制长度）
     */
    public String sanitizeSpeech(String aiOutput, int maxLength) {
        String cleaned = aiOutput
            .replaceAll("```.*?```", "")
            .replaceAll("[\\n\\r]+", " ")
            .trim();
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength) + "...";
        }
        return cleaned;
    }
}
```

## 6. 上下文构建器

```java
@Component
public class GameContextBuilder {

    /**
     * 从 GameState 构建 AI 决策所需的完整上下文
     */
    public GameContext build(GameState state, Room room, String aiPlayerId) {
        GamePlayerState aiPlayer = findPlayer(state, aiPlayerId);

        List<PlayerInfo> players = state.getPlayers().stream()
            .map(p -> new PlayerInfo(
                p.getPlayerId(),
                p.getDisplayName(),
                p.getSeatNumber(),
                p.isAlive(),
                p.isAi(),
                // 只暴露已公开的身份（出局后揭示的）
                !p.isAlive() ? p.getRole() : null
            ))
            .toList();

        List<SpeechRecord> speeches = extractSpeeches(state.getLogs());
        List<VoteRecord> votes = extractVotes(state);

        Map<String, Object> extra = new HashMap<>();
        // 狼人杀：预言家查验结果（仅预言家 AI 可见）
        if ("SEER".equals(aiPlayer.getRole())) {
            extra.put("seerResults", state.getData().get("seerResults"));
        }
        // 狼人杀：狼人知道队友
        if (aiPlayer.getRole().startsWith("WEREWOLF")) {
            List<String> wolfTeam = state.getPlayers().stream()
                .filter(p -> p.getRole().startsWith("WEREWOLF"))
                .map(GamePlayerState::getDisplayName)
                .toList();
            extra.put("wolfTeam", wolfTeam);
        }

        return new GameContext(
            room.getGameId(), state.getRoundNumber(), state.getPhase(),
            aiPlayerId, aiPlayer.getRole(), aiPlayer.getWord(),
            aiPlayer.getSeatNumber(), players, speeches, votes, extra
        );
    }

    private List<SpeechRecord> extractSpeeches(List<GameLogEntry> logs) {
        // 从日志中提取发言记录，解析 "xxx：yyy" 格式
        return logs.stream()
            .filter(log -> "speak".equals(log.getType()))
            .map(log -> parseSpeechLog(log))
            .filter(Objects::nonNull)
            .toList();
    }
}
```

## 7. 异步与超时处理

AI 决策调用 LLM 可能耗时较长，需要异步处理：

```java
@Service
public class AiDecisionServiceImpl implements AiDecisionService {

    private final AiGrpcClient aiClient;
    private final GameContextBuilder contextBuilder;
    private final AiDecisionParser parser;
    private final PromptProperties prompts;

    private static final Duration AI_TIMEOUT = Duration.ofSeconds(10);

    @Override
    public String generateSpeech(GameContext context, Persona persona) {
        String prompt = buildSpeechPrompt(context, persona);
        try {
            String response = aiClient.chat(prompt)
                .timeout(AI_TIMEOUT)
                .block();
            return parser.sanitizeSpeech(response, 100);
        } catch (Exception e) {
            log.warn("AI 发言生成失败，使用兜底模板", e);
            return fallbackSpeech(context);
        }
    }

    @Override
    public String decideVote(GameContext context, Persona persona) {
        String prompt = buildVotePrompt(context, persona);
        try {
            String response = aiClient.chat(prompt)
                .timeout(AI_TIMEOUT)
                .block();
            Optional<Integer> seat = parser.parseVoteTarget(response, context.players());
            if (seat.isPresent()) {
                return seatToPlayerId(context, seat.get());
            }
        } catch (Exception e) {
            log.warn("AI 投票决策失败，使用随机投票", e);
        }
        return randomVote(context);
    }

    // 兜底：基于简单规则的发言
    private String fallbackSpeech(GameContext context) {
        if ("undercover".equals(context.gameType())) {
            return "我觉得这个东西大家都很熟悉，不多说了。";
        }
        return "我暂时没有明确的怀疑对象，再观察一下。";
    }
}
```

## 8. 数据库变更

### personas 表扩展

```sql
ALTER TABLE personas ADD COLUMN speech_style VARCHAR(50) DEFAULT '正常';
ALTER TABLE personas ADD COLUMN strategy_style VARCHAR(50) DEFAULT '均衡型';
ALTER TABLE personas ADD COLUMN difficulty_level VARCHAR(20) DEFAULT 'NORMAL';
```

## 9. 配置项

```yaml
# application.yml
ai-decision:
  enabled: true
  timeout-seconds: 10
  max-speech-length: 100
  fallback-on-error: true    # AI 调用失败时是否使用兜底模板
  model: "default"           # 使用的 AI 模型（可按难度切换）
```

## 10. 与现有代码的集成点

需要修改的现有文件：

| 文件 | 修改内容 |
|------|----------|
| `GamePlayService.java` | `autoAdvanceUndercover` 和 `autoAdvanceWerewolfDay` 中调用新的 `AiDecisionService` 替代硬编码逻辑 |
| `GamePlayService.java` | `fillAiVotes` 改为调用 `AiDecisionService.decideVote` |
| `GamePlayService.java` | `autoFillNightActions` 改为调用 `AiDecisionService.decideNightAction` |
| `AiGameSpeechService.java` | 可逐步废弃，功能迁移到 `AiDecisionServiceImpl` |
| `Persona.java` | 新增 `speechStyle`、`strategyStyle`、`difficultyLevel` 字段 |
| `prompt.yml` | 新增完整的 AI 决策 prompt 模板 |

## 11. 测试要点

- [ ] AI 发言是否参考了上下文（对比有无上下文的输出质量）
- [ ] AI 投票是否有逻辑（统计投票准确率 vs 随机基线）
- [ ] 不同 Persona 的发言风格是否有差异
- [ ] 不同难度等级的行为差异
- [ ] AI 服务超时时的兜底行为
- [ ] 决策解析器对各种格式的容错能力
- [ ] 狼人 AI 是否正确隐藏身份（不在发言中暴露）
- [ ] 预言家 AI 是否正确使用查验信息
