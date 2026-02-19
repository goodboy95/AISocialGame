# 模块 03：游戏结算与大揭秘

> 优先级：P0 | 阶段：第一阶段 | 依赖：模块 01（WebSocket） | 被依赖：模块 09（对局回放）

## 1. 背景与目标

当前结算体验：游戏结束后，操作区显示一行文字 `"对局结束，获胜方：xxx"`，日志中显示 `"游戏结束，获胜方：好人/平民阵营"`。没有身份揭示、没有数据回顾、没有情感高潮。

社交推理游戏的核心爽点之一就是结算时的"大揭秘"——所有隐藏信息公开的那一刻。这个模块要把这个时刻做到位。

## 2. 结算流程设计

```
游戏结束
  │
  ├─► 1. 胜负宣告（1.5s）
  │     全屏展示获胜阵营，配合视觉效果
  │
  ├─► 2. 身份揭示（每人 0.8s，逐个翻牌）
  │     按座位顺序逐个揭示每位玩家的真实身份
  │
  ├─► 3. 关键事件回顾（可选展开）
  │     时间线形式展示关键转折点
  │
  ├─► 4. 个人战报
  │     本局表现数据 + 获得奖励
  │
  └─► 5. 操作区
        再来一局 / 分享战绩 / 返回大厅
```

## 3. 后端数据结构

### 3.1 结算数据扩展

在 `finishGame()` 方法中构建完整的结算数据：

```java
public record SettlementData(
    String winner,                    // "WEREWOLF" | "CIVILIAN" | "UNDERCOVER"
    String winnerText,                // "狼人阵营" | "好人阵营" | "卧底阵营"
    List<PlayerReveal> playerReveals, // 所有玩家身份揭示
    List<KeyEvent> keyEvents,         // 关键事件
    Map<String, PlayerReport> reports // 每个玩家的个人战报
) {}

public record PlayerReveal(
    String playerId,
    String displayName,
    String avatar,
    int seatNumber,
    String role,                      // 真实身份
    String roleDisplayName,           // "狼人" | "预言家" | "平民" | "卧底"
    String word,                      // 谁是卧底的词语
    boolean alive,                    // 最终是否存活
    boolean isWinner                  // 是否属于获胜方
) {}

public record KeyEvent(
    int round,
    String phase,
    String type,                      // "KILL" | "SAVE" | "VOTE_OUT" | "CLOSE_VOTE" | "SEER_CHECK"
    String description,               // "第2夜，狼人击杀了小明"
    List<String> involvedPlayerIds
) {}

public record PlayerReport(
    String playerId,
    int survivalRounds,               // 存活轮数
    int totalRounds,                  // 总轮数
    int correctVotes,                 // 正确投票次数（投中了对立阵营）
    int totalVotes,                   // 总投票次数
    double voteAccuracy,              // 投票准确率
    boolean mvp,                      // 是否 MVP
    int coinsEarned,                  // 获得金币
    int scoreChange                   // 积分变化
) {}
```

### 3.2 关键事件提取

```java
private List<KeyEvent> extractKeyEvents(GameState state) {
    List<KeyEvent> events = new ArrayList<>();

    for (GameLogEntry log : state.getLogs()) {
        if ("night".equals(log.getType()) && log.getMessage().contains("死亡")) {
            events.add(new KeyEvent(
                state.getRoundNumber(), "NIGHT", "KILL",
                log.getMessage(), List.of()
            ));
        }
        if ("vote".equals(log.getType()) && log.getMessage().contains("出局")) {
            events.add(new KeyEvent(
                state.getRoundNumber(), "DAY", "VOTE_OUT",
                log.getMessage(), List.of()
            ));
        }
        // 平票也是关键事件
        if ("vote".equals(log.getType()) && log.getMessage().contains("平票")) {
            events.add(new KeyEvent(
                state.getRoundNumber(), "DAY", "CLOSE_VOTE",
                log.getMessage(), List.of()
            ));
        }
    }
    return events;
}
```

### 3.3 MVP 计算

```java
private String calculateMvp(GameState state, Set<String> winnerIds) {
    // MVP = 获胜方中投票准确率最高 + 存活最久的玩家
    return winnerIds.stream()
        .map(id -> playerById(state, id))
        .filter(Objects::nonNull)
        .max(Comparator.comparingDouble(p -> calculateMvpScore(state, p)))
        .map(GamePlayerState::getPlayerId)
        .orElse(null);
}

private double calculateMvpScore(GameState state, GamePlayerState player) {
    double survivalScore = player.isAlive() ? 1.0 : 0.5;
    double voteScore = calculateVoteAccuracy(state, player.getPlayerId());
    return survivalScore * 0.4 + voteScore * 0.6;
}
```

## 4. API 变更

结算数据通过现有的 `GET /state` 接口返回，当 `phase == "SETTLEMENT"` 时，`extra` 字段包含完整的 `SettlementData`：

```json
{
  "phase": "SETTLEMENT",
  "winner": "CIVILIAN",
  "extra": {
    "settlement": {
      "winner": "CIVILIAN",
      "winnerText": "好人阵营",
      "playerReveals": [
        {
          "playerId": "p1",
          "displayName": "张三",
          "seatNumber": 0,
          "role": "WEREWOLF",
          "roleDisplayName": "狼人",
          "alive": false,
          "isWinner": false
        }
      ],
      "keyEvents": [...],
      "reports": {
        "p1": { "survivalRounds": 2, "correctVotes": 1, "coinsEarned": 5 }
      }
    }
  }
}
```

## 5. 前端实现

### 5.1 结算页面组件结构

```
SettlementOverlay (全屏覆盖层)
├── VictoryBanner          胜负宣告动画
├── RevealCarousel         身份翻牌序列
│   └── RevealCard × N     单个玩家翻牌卡片
├── KeyEventTimeline       关键事件时间线
├── PersonalReport         个人战报卡片
└── ActionBar              操作按钮区
```

### 5.2 VictoryBanner — 胜负宣告

```tsx
const VictoryBanner = ({ winner, winnerText }: { winner: string; winnerText: string }) => {
  const isMyWin = /* 判断当前玩家是否属于获胜方 */;

  return (
    <motion.div
      initial={{ scale: 0, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ type: "spring", duration: 0.8 }}
      className="text-center py-12"
    >
      <motion.div
        animate={{ rotate: [0, -5, 5, 0] }}
        transition={{ delay: 0.8, duration: 0.5 }}
        className="text-6xl mb-4"
      >
        {isMyWin ? "🏆" : "💀"}
      </motion.div>
      <h1 className={`text-3xl font-bold ${isMyWin ? 'text-amber-500' : 'text-slate-500'}`}>
        {isMyWin ? "胜利！" : "失败..."}
      </h1>
      <p className="text-lg text-muted-foreground mt-2">
        获胜方：{winnerText}
      </p>
    </motion.div>
  );
};
```

### 5.3 RevealCard — 身份翻牌

```tsx
const RevealCard = ({ player, index }: { player: PlayerReveal; index: number }) => {
  const [revealed, setRevealed] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setRevealed(true), index * 800);
    return () => clearTimeout(timer);
  }, [index]);

  const roleColor = {
    WEREWOLF: "bg-red-500",
    UNDERCOVER: "bg-purple-500",
    SEER: "bg-blue-500",
    WITCH: "bg-green-500",
    HUNTER: "bg-orange-500",
    VILLAGER: "bg-slate-400",
    CIVILIAN: "bg-slate-400",
  }[player.role] || "bg-slate-400";

  return (
    <motion.div
      initial={{ rotateY: 180 }}
      animate={revealed ? { rotateY: 0 } : {}}
      transition={{ duration: 0.6, ease: "easeOut" }}
      className="relative w-24 h-32"
      style={{ perspective: 1000 }}
    >
      {/* 正面：身份信息 */}
      <div className={`absolute inset-0 rounded-xl ${roleColor} text-white
        flex flex-col items-center justify-center p-2 backface-hidden`}>
        <Avatar className="h-10 w-10 border-2 border-white/50">
          <AvatarImage src={player.avatar} />
          <AvatarFallback>{player.displayName[0]}</AvatarFallback>
        </Avatar>
        <div className="text-xs font-bold mt-1 truncate w-full text-center">
          {player.displayName}
        </div>
        <Badge className="mt-1 text-[10px]">{player.roleDisplayName}</Badge>
        {player.word && (
          <div className="text-[10px] mt-1 opacity-80">"{player.word}"</div>
        )}
        {!player.alive && (
          <div className="absolute top-1 right-1 text-xs">💀</div>
        )}
      </div>
    </motion.div>
  );
};
```

### 5.4 KeyEventTimeline — 关键事件时间线

```tsx
const KeyEventTimeline = ({ events }: { events: KeyEvent[] }) => (
  <div className="space-y-3 py-4">
    <h3 className="text-sm font-semibold text-muted-foreground">关键事件回顾</h3>
    <div className="relative pl-6 border-l-2 border-slate-200 space-y-4">
      {events.map((event, idx) => (
        <motion.div
          key={idx}
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: idx * 0.3 }}
          className="relative"
        >
          <div className="absolute -left-[25px] w-3 h-3 rounded-full bg-white border-2 border-slate-300" />
          <div className="text-xs text-muted-foreground">
            第{event.round}{event.phase === "NIGHT" ? "夜" : "天"}
          </div>
          <div className="text-sm">{event.description}</div>
        </motion.div>
      ))}
    </div>
  </div>
);
```

### 5.5 PersonalReport — 个人战报

```tsx
const PersonalReport = ({ report }: { report: PlayerReport }) => (
  <Card className="p-4">
    <h3 className="text-sm font-semibold mb-3">我的战报</h3>
    <div className="grid grid-cols-3 gap-4 text-center">
      <div>
        <div className="text-2xl font-bold text-blue-600">
          {report.survivalRounds}/{report.totalRounds}
        </div>
        <div className="text-xs text-muted-foreground">存活轮数</div>
      </div>
      <div>
        <div className="text-2xl font-bold text-green-600">
          {Math.round(report.voteAccuracy * 100)}%
        </div>
        <div className="text-xs text-muted-foreground">投票准确率</div>
      </div>
      <div>
        <div className="text-2xl font-bold text-amber-500">
          +{report.coinsEarned}
        </div>
        <div className="text-xs text-muted-foreground">获得金币</div>
      </div>
    </div>
    {report.mvp && (
      <div className="mt-3 text-center">
        <Badge className="bg-amber-100 text-amber-700 border-amber-200">
          ⭐ 本局 MVP
        </Badge>
      </div>
    )}
  </Card>
);
```

### 5.6 ActionBar — 操作按钮

```tsx
const ActionBar = ({ roomId, gameId }: { roomId: string; gameId: string }) => (
  <div className="flex gap-3 justify-center pt-4">
    <Button onClick={handlePlayAgain} className="flex-1 max-w-[200px]">
      再来一局
    </Button>
    <Button variant="outline" onClick={handleShare}>
      <Share2 className="h-4 w-4 mr-2" /> 分享战绩
    </Button>
    <Button variant="ghost" onClick={() => navigate(`/game/${gameId}`)}>
      返回大厅
    </Button>
  </div>
);
```

## 6. 分享战绩卡片

生成一张可分享的图片卡片：

```
┌──────────────────────────────┐
│  🏆 AI 社交游戏 — 狼人杀     │
│                              │
│  身份：预言家    结果：胜利   │
│  存活：3/4 轮   准确率：75%  │
│                              │
│  "我在第2夜查验了3号是狼人"   │
│                              │
│  ───────────────────────     │
│  扫码加入，一起来玩！         │
│  [二维码]                    │
└──────────────────────────────┘
```

使用 `html2canvas` 将 DOM 渲染为图片，或后端生成 SVG/PNG。

## 7. "再来一局"流程

```
点击"再来一局"
  │
  ├─► 房主点击 → 重置房间状态为 WAITING，保留所有座位
  │                WebSocket 广播 ROOM_RESET 事件
  │                所有玩家自动回到等待界面
  │
  └─► 非房主点击 → 发送"准备"信号
                    等待房主开始新一局
```

后端需要新增 `POST /api/games/{gameId}/rooms/{roomId}/reset` 接口：

```java
public GameStateResponse resetRoom(String gameId, String roomId, User user, String playerIdHeader) {
    Room room = roomService.getRoom(roomId);
    String actorId = resolvePlayerId(room, user, playerIdHeader);
    if (!isHost(room, actorId)) {
        throw new ApiException(HttpStatus.FORBIDDEN, "只有房主可以重置房间");
    }
    gameStateRepository.deleteById(roomId);
    roomService.updateStatus(roomId, RoomStatus.WAITING);
    // 推送重置事件
    gamePushService.pushStateChange(roomId,
        new GameStateEvent("ROOM_RESET", "WAITING", 0, null, null));
    return buildWaitingResponse(room, actorId);
}
```

## 8. 动画依赖

前端新增依赖：

```json
{
  "framer-motion": "^11.0.0",
  "html2canvas": "^1.4.1"
}
```

`framer-motion` 用于翻牌、弹入等动画效果。如果项目已有其他动画方案可替代。

## 9. 数据库变更

无。结算数据通过 `game_states.data` JSON 字段存储，不需要新表。

## 10. 测试要点

- [ ] 结算数据完整性（所有玩家身份、关键事件、个人战报）
- [ ] MVP 计算逻辑正确性
- [ ] 翻牌动画在不同玩家数量下的表现（4人 vs 12人）
- [ ] "再来一局"流程：房间状态正确重置
- [ ] 分享卡片生成（图片内容正确、可下载）
- [ ] WebSocket 推送结算事件的及时性
