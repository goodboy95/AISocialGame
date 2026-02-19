# 模块 08：成就系统

> 优先级：P1 | 阶段：第二阶段 | 依赖：无 | 被依赖：无

## 1. 背景与目标

当前平台的留存激励仅有每日签到和排行榜积分。缺少中长期目标感和里程碑反馈。

目标：构建事件驱动的成就系统，在玩家达成特定条件时解锁成就徽章，给予金币奖励，并在个人主页展示。系统设计为通用框架，新游戏类型只需注册新的成就定义即可。

## 2. 架构设计

```
游戏事件（结算、投票、发言等）
  │
  ├─► Spring ApplicationEvent
  │
  ├─► AchievementEventListener
  │     │
  │     ├─► 加载玩家已解锁成就
  │     ├─► 遍历所有成就定义，检查条件
  │     ├─► 条件满足 → 解锁成就 → 发放奖励
  │     └─► WebSocket 推送解锁通知
  │
  └─► 成就定义注册表（AchievementRegistry）
        可按游戏类型注册新成就
```

## 3. 数据模型

### 3.1 成就定义表

```sql
CREATE TABLE achievement_definitions (
    id              VARCHAR(50) PRIMARY KEY,     -- "first_win", "wolf_master" 等
    name            VARCHAR(100) NOT NULL,       -- 显示名称
    description     VARCHAR(500) NOT NULL,       -- 描述
    icon            VARCHAR(200),                -- 图标 URL 或 emoji
    category        VARCHAR(50) NOT NULL,        -- GENERAL | UNDERCOVER | WEREWOLF
    rarity          VARCHAR(20) DEFAULT 'COMMON', -- COMMON | RARE | EPIC | LEGENDARY
    condition_type  VARCHAR(50) NOT NULL,        -- GAME_COUNT | WIN_COUNT | WIN_STREAK | CUSTOM
    condition_value INT DEFAULT 1,               -- 条件阈值
    condition_extra JSON,                        -- 额外条件参数
    reward_coins    INT DEFAULT 0,               -- 奖励金币
    reward_score    INT DEFAULT 0,               -- 奖励积分
    sort_order      INT DEFAULT 0,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2 玩家成就记录表

```sql
CREATE TABLE player_achievements (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id       VARCHAR(36) NOT NULL,
    achievement_id  VARCHAR(50) NOT NULL,
    unlocked_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    game_room_id    VARCHAR(36),                 -- 解锁时的对局房间
    UNIQUE KEY uk_player_achievement (player_id, achievement_id),
    INDEX idx_player (player_id)
);
```

### 3.3 成就进度表（用于累计型成就）

```sql
CREATE TABLE achievement_progress (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id       VARCHAR(36) NOT NULL,
    achievement_id  VARCHAR(50) NOT NULL,
    current_value   INT DEFAULT 0,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_progress (player_id, achievement_id),
    INDEX idx_player (player_id)
);
```

## 4. 成就定义

### 4.1 通用成就

| ID | 名称 | 条件 | 稀有度 | 奖励 |
|----|------|------|--------|------|
| `first_game` | 初出茅庐 | 完成第 1 局游戏 | COMMON | 10 币 |
| `ten_games` | 常客 | 完成 10 局游戏 | COMMON | 30 币 |
| `fifty_games` | 老玩家 | 完成 50 局游戏 | RARE | 100 币 |
| `first_win` | 首胜 | 赢得第 1 局 | COMMON | 20 币 |
| `win_streak_3` | 三连胜 | 连续赢 3 局 | RARE | 50 币 |
| `win_streak_5` | 五连胜 | 连续赢 5 局 | EPIC | 100 币 |
| `social_10` | 社交新星 | 与 10 个不同玩家对局 | COMMON | 30 币 |
| `social_50` | 社交达人 | 与 50 个不同玩家对局 | RARE | 80 币 |

### 4.2 谁是卧底成就

| ID | 名称 | 条件 | 稀有度 | 奖励 |
|----|------|------|--------|------|
| `uc_spy_win` | 潜伏者 | 作为卧底获胜 1 次 | COMMON | 20 币 |
| `uc_spy_master` | 影帝 | 作为卧底获胜 10 次 | EPIC | 150 币 |
| `uc_detective` | 火眼金睛 | 连续 3 局正确投出卧底 | RARE | 50 币 |
| `uc_survivor` | 幸存者 | 作为平民存活到最后 5 次 | RARE | 50 币 |
| `uc_blank_win` | 白板逆袭 | 作为白板获胜 | LEGENDARY | 200 币 |

### 4.3 狼人杀成就

| ID | 名称 | 条件 | 稀有度 | 奖励 |
|----|------|------|--------|------|
| `ww_wolf_win` | 暗夜猎手 | 作为狼人获胜 1 次 | COMMON | 20 币 |
| `ww_wolf_master` | 狼王 | 作为狼人获胜 10 次 | EPIC | 150 币 |
| `ww_seer_perfect` | 神算 | 作为预言家查验全部正确 | RARE | 80 币 |
| `ww_witch_save` | 妙手回春 | 作为女巫成功救人 5 次 | RARE | 50 币 |
| `ww_last_stand` | 绝地反击 | 最后一个好人存活并获胜 | LEGENDARY | 200 币 |
| `ww_perfect_vote` | 明察秋毫 | 单局所有投票都投中狼人 | EPIC | 100 币 |

## 5. 后端实现

### 5.1 游戏事件定义

```java
// 游戏结算事件
public record GameFinishedEvent(
    String roomId,
    String gameId,
    String winner,                    // 获胜阵营
    List<GamePlayerState> players,
    Set<String> winnerIds,
    int totalRounds
) implements ApplicationEvent {}
```

### 5.2 成就检查器接口

```java
public interface AchievementChecker {
    /**
     * 检查玩家是否满足该成就的解锁条件
     * @return true 表示应该解锁
     */
    boolean check(String playerId, AchievementDefinition definition,
                  GameFinishedEvent event, AchievementProgress progress);
}
```

### 5.3 内置检查器

```java
@Component
public class GameCountChecker implements AchievementChecker {
    @Override
    public boolean check(String playerId, AchievementDefinition def,
                         GameFinishedEvent event, AchievementProgress progress) {
        if (!"GAME_COUNT".equals(def.getConditionType())) return false;
        return progress.getCurrentValue() + 1 >= def.getConditionValue();
    }
}

@Component
public class WinCountChecker implements AchievementChecker {
    @Override
    public boolean check(String playerId, AchievementDefinition def,
                         GameFinishedEvent event, AchievementProgress progress) {
        if (!"WIN_COUNT".equals(def.getConditionType())) return false;
        boolean isWinner = event.winnerIds().contains(playerId);
        if (!isWinner) return false;
        // 检查额外条件（如特定角色）
        String requiredRole = getExtraCondition(def, "role");
        if (requiredRole != null) {
            GamePlayerState player = findPlayer(event, playerId);
            if (player == null || !requiredRole.equals(player.getRole())) return false;
        }
        return progress.getCurrentValue() + 1 >= def.getConditionValue();
    }
}

@Component
public class WinStreakChecker implements AchievementChecker {
    @Override
    public boolean check(String playerId, AchievementDefinition def,
                         GameFinishedEvent event, AchievementProgress progress) {
        if (!"WIN_STREAK".equals(def.getConditionType())) return false;
        boolean isWinner = event.winnerIds().contains(playerId);
        // 连胜：赢了 +1，输了归零
        int newStreak = isWinner ? progress.getCurrentValue() + 1 : 0;
        return newStreak >= def.getConditionValue();
    }
}
```

### 5.4 成就事件监听器

```java
@Component
public class AchievementEventListener {

    private final AchievementDefinitionRepository definitionRepo;
    private final PlayerAchievementRepository achievementRepo;
    private final AchievementProgressRepository progressRepo;
    private final List<AchievementChecker> checkers;
    private final WalletService walletService;
    private final GamePushService pushService;

    @EventListener
    public void onGameFinished(GameFinishedEvent event) {
        List<AchievementDefinition> definitions = definitionRepo.findByEnabled(true);

        for (GamePlayerState player : event.players()) {
            if (player.isAi()) continue; // AI 不计成就

            for (AchievementDefinition def : definitions) {
                // 跳过已解锁的
                if (achievementRepo.existsByPlayerIdAndAchievementId(
                        player.getPlayerId(), def.getId())) continue;

                // 跳过不匹配游戏类型的
                if (!"GENERAL".equals(def.getCategory())
                    && !event.gameId().equals(def.getCategory().toLowerCase())) continue;

                AchievementProgress progress = progressRepo
                    .findByPlayerIdAndAchievementId(player.getPlayerId(), def.getId())
                    .orElse(new AchievementProgress(player.getPlayerId(), def.getId(), 0));

                for (AchievementChecker checker : checkers) {
                    if (checker.check(player.getPlayerId(), def, event, progress)) {
                        unlockAchievement(player, def, event.roomId());
                        break;
                    }
                }

                // 更新进度
                updateProgress(player, def, event);
            }
        }
    }

    private void unlockAchievement(GamePlayerState player, AchievementDefinition def, String roomId) {
        PlayerAchievement achievement = new PlayerAchievement();
        achievement.setPlayerId(player.getPlayerId());
        achievement.setAchievementId(def.getId());
        achievement.setGameRoomId(roomId);
        achievementRepo.save(achievement);

        // 发放奖励
        if (def.getRewardCoins() > 0) {
            walletService.addCoins(player.getPlayerId(), def.getRewardCoins(), "成就奖励：" + def.getName());
        }

        // 推送解锁通知
        pushService.pushPrivate(player.getPlayerId(), new PrivateEvent("ACHIEVEMENT_UNLOCKED", Map.of(
            "id", def.getId(),
            "name", def.getName(),
            "description", def.getDescription(),
            "icon", def.getIcon(),
            "rarity", def.getRarity(),
            "rewardCoins", def.getRewardCoins()
        )));
    }
}
```

## 6. API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/achievements` | 所有成就定义列表 |
| GET | `/api/achievements/my` | 当前用户已解锁成就 + 进度 |
| GET | `/api/achievements/player/{playerId}` | 查看某玩家的成就（公开） |

## 7. 前端设计

### 7.1 成就解锁 Toast

```tsx
// 通过 WebSocket 接收
if (event.type === "ACHIEVEMENT_UNLOCKED") {
  toast(
    <div className="flex items-center gap-3">
      <div className="text-3xl">{event.payload.icon}</div>
      <div>
        <div className="font-bold text-sm">成就解锁！</div>
        <div className="text-sm">{event.payload.name}</div>
        <div className="text-xs text-muted-foreground">{event.payload.description}</div>
        {event.payload.rewardCoins > 0 && (
          <div className="text-xs text-amber-600 mt-0.5">+{event.payload.rewardCoins} 金币</div>
        )}
      </div>
    </div>,
    { duration: 5000 }
  );
}
```

### 7.2 个人主页成就展示

```tsx
const AchievementGrid = ({ achievements, unlocked }: Props) => {
  const rarityBorder = {
    COMMON: "border-slate-300",
    RARE: "border-blue-400",
    EPIC: "border-purple-500",
    LEGENDARY: "border-amber-500",
  };

  return (
    <div className="grid grid-cols-4 md:grid-cols-6 gap-3">
      {achievements.map(def => {
        const isUnlocked = unlocked.some(u => u.achievementId === def.id);
        return (
          <div key={def.id}
            className={`flex flex-col items-center p-3 rounded-xl border-2
              ${isUnlocked ? rarityBorder[def.rarity] : 'border-slate-200 opacity-40'}
              ${isUnlocked ? 'bg-white' : 'bg-slate-50 grayscale'}`}
          >
            <div className="text-2xl">{def.icon}</div>
            <div className="text-xs font-medium mt-1 text-center truncate w-full">
              {def.name}
            </div>
          </div>
        );
      })}
    </div>
  );
};
```

## 8. 通用性设计

新游戏只需在 `achievement_definitions` 表中插入新记录，并实现对应的 `AchievementChecker`（如果需要自定义逻辑）。标准的 `GAME_COUNT`、`WIN_COUNT`、`WIN_STREAK` 类型无需额外代码。

## 9. 测试要点

- [ ] 各类型成就的解锁条件判断
- [ ] 累计进度正确更新（赢了 +1，连胜断了归零）
- [ ] 金币奖励正确发放
- [ ] 同一成就不重复解锁
- [ ] AI 玩家不触发成就
- [ ] WebSocket 推送解锁通知
- [ ] 成就展示页面（已解锁/未解锁/进度）
