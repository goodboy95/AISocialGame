# 模块 14：新手引导框架

> 优先级：P2 | 阶段：第三阶段 | 依赖：模块 11（GameEngine 抽象层） | 被依赖：无

## 1. 背景与目标

当前没有任何新手引导。新玩家进入游戏后面对的是一个功能完整但没有说明的界面，不知道该做什么、什么时候该操作、各个角色有什么能力。

目标：构建可配置的新手引导框架，包括交互式教程、练习模式、规则百科，降低新玩家的上手门槛。框架本身与游戏类型无关，每个游戏只需提供引导步骤配置。

## 2. 功能模块

| 功能 | 说明 |
|------|------|
| 交互式教程 | 引导式的第一局体验，高亮 UI 元素并解释操作 |
| 练习模式 | 与 AI 对战，带提示和建议，不影响排名 |
| 规则百科 | 游戏规则、角色说明、策略技巧的查阅页面 |
| 上下文提示 | 首次遇到新阶段/新操作时的 tooltip 提示 |
| 引导进度 | 记录玩家已完成的引导步骤，不重复显示 |

## 3. 引导步骤配置模型

### 3.1 数据结构

```typescript
interface TutorialStep {
  id: string;                    // 步骤唯一标识
  gameId: string;                // 所属游戏
  phase: string;                 // 触发阶段
  targetSelector: string;        // 高亮目标的 CSS 选择器
  title: string;                 // 标题
  content: string;               // 说明文字
  position: "top" | "bottom" | "left" | "right";  // 提示框位置
  action?: "click" | "input" | "wait";  // 需要用户执行的操作
  condition?: string;            // 显示条件（如 "isMyTurn"）
  order: number;                 // 排序
}

interface TutorialConfig {
  gameId: string;
  steps: TutorialStep[];
}
```

### 3.2 谁是卧底教程配置

```typescript
const undercoverTutorial: TutorialConfig = {
  gameId: "undercover",
  steps: [
    {
      id: "uc-word",
      gameId: "undercover",
      phase: "DESCRIPTION",
      targetSelector: "[data-tutorial='my-word']",
      title: "你的词语",
      content: "这是你拿到的词语。大多数人拿到相同的词，但有一个人（卧底）拿到的是不同的词。你需要通过描述来找出谁是卧底。",
      position: "bottom",
      order: 1,
    },
    {
      id: "uc-speak",
      gameId: "undercover",
      phase: "DESCRIPTION",
      targetSelector: "[data-tutorial='speak-input']",
      title: "描述你的词语",
      content: "轮到你时，用一句话描述你的词语。注意不要说得太直白，否则卧底会猜到你的词。如果你是卧底，要尽量模仿其他人的描述风格。",
      position: "top",
      condition: "isMyTurn",
      action: "input",
      order: 2,
    },
    {
      id: "uc-listen",
      gameId: "undercover",
      phase: "DESCRIPTION",
      targetSelector: "[data-tutorial='game-log']",
      title: "注意听其他人的描述",
      content: "仔细听每个人的描述，找出与大多数人不同的那个。描述偏离主题的人可能就是卧底。",
      position: "left",
      condition: "!isMyTurn",
      order: 3,
    },
    {
      id: "uc-vote",
      gameId: "undercover",
      phase: "VOTING",
      targetSelector: "[data-tutorial='vote-panel']",
      title: "投票环节",
      content: "选择你认为是卧底的玩家并投票。得票最多的人将被淘汰并揭示身份。",
      position: "top",
      action: "click",
      order: 4,
    },
  ],
};
```

### 3.3 狼人杀教程配置

```typescript
const werewolfTutorial: TutorialConfig = {
  gameId: "werewolf",
  steps: [
    {
      id: "ww-role",
      gameId: "werewolf",
      phase: "NIGHT",
      targetSelector: "[data-tutorial='my-role']",
      title: "你的身份",
      content: "这是你的角色。狼人需要在夜晚击杀好人；好人需要在白天投票放逐狼人。记住你的身份，不要暴露给其他人。",
      position: "bottom",
      order: 1,
    },
    {
      id: "ww-night",
      gameId: "werewolf",
      phase: "NIGHT",
      targetSelector: "[data-tutorial='action-panel']",
      title: "夜晚行动",
      content: "夜晚阶段，有特殊能力的角色需要执行行动。狼人选择击杀目标，预言家选择查验对象，女巫决定是否使用药水。",
      position: "top",
      order: 2,
    },
    {
      id: "ww-discuss",
      gameId: "werewolf",
      phase: "DAY_DISCUSS",
      targetSelector: "[data-tutorial='speak-input']",
      title: "白天讨论",
      content: "天亮后，所有人轮流发言讨论。分享你的观察和推理，但如果你是狼人，要小心隐藏身份。",
      position: "top",
      condition: "isMyTurn",
      order: 3,
    },
    {
      id: "ww-vote",
      gameId: "werewolf",
      phase: "DAY_VOTE",
      targetSelector: "[data-tutorial='vote-panel']",
      title: "投票放逐",
      content: "讨论结束后进入投票。选择你认为是狼人的玩家。得票最多的人将被放逐。",
      position: "top",
      order: 4,
    },
  ],
};
```

## 4. 前端引导组件

### 4.1 Tutorial Overlay

```tsx
// components/tutorial/TutorialOverlay.tsx
interface TutorialOverlayProps {
  steps: TutorialStep[];
  currentPhase: string;
  isMyTurn: boolean;
  onComplete: () => void;
}

const TutorialOverlay = ({ steps, currentPhase, isMyTurn, onComplete }: TutorialOverlayProps) => {
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());

  // 找到当前应该显示的步骤
  const activeStep = useMemo(() => {
    return steps.find(step => {
      if (dismissed.has(step.id)) return false;
      if (step.phase !== currentPhase) return false;
      if (step.condition === "isMyTurn" && !isMyTurn) return false;
      if (step.condition === "!isMyTurn" && isMyTurn) return false;
      return true;
    });
  }, [steps, currentPhase, isMyTurn, dismissed]);

  if (!activeStep) return null;

  const targetEl = document.querySelector(activeStep.targetSelector);
  if (!targetEl) return null;

  const rect = targetEl.getBoundingClientRect();

  return (
    <>
      {/* 半透明遮罩，高亮目标区域 */}
      <div className="fixed inset-0 z-40">
        <svg className="w-full h-full">
          <defs>
            <mask id="spotlight">
              <rect width="100%" height="100%" fill="white" />
              <rect x={rect.left - 8} y={rect.top - 8}
                width={rect.width + 16} height={rect.height + 16}
                rx="8" fill="black" />
            </mask>
          </defs>
          <rect width="100%" height="100%" fill="rgba(0,0,0,0.6)" mask="url(#spotlight)" />
        </svg>
      </div>

      {/* 提示卡片 */}
      <div className="fixed z-50" style={getTooltipPosition(rect, activeStep.position)}>
        <Card className="w-72 shadow-xl border-blue-200">
          <CardContent className="p-4">
            <h4 className="font-bold text-sm mb-1">{activeStep.title}</h4>
            <p className="text-sm text-muted-foreground leading-relaxed">
              {activeStep.content}
            </p>
            <div className="flex justify-between items-center mt-3">
              <span className="text-xs text-muted-foreground">
                {currentStepIndex + 1}/{steps.length}
              </span>
              <div className="flex gap-2">
                <Button size="sm" variant="ghost" onClick={() => {
                  setDismissed(prev => new Set([...prev, activeStep.id]));
                }}>
                  知道了
                </Button>
                <Button size="sm" variant="ghost" onClick={onComplete}
                  className="text-muted-foreground">
                  跳过教程
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  );
};
```

### 4.2 引导进度管理

```typescript
// hooks/useTutorialProgress.ts
const STORAGE_KEY = "tutorial_progress";

export function useTutorialProgress(gameId: string) {
  const [completed, setCompleted] = useState<Set<string>>(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      const data = JSON.parse(stored);
      return new Set(data[gameId] || []);
    }
    return new Set();
  });

  const markCompleted = (stepId: string) => {
    setCompleted(prev => {
      const next = new Set([...prev, stepId]);
      const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
      stored[gameId] = [...next];
      localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
      return next;
    });
  };

  const markAllCompleted = () => {
    // 跳过教程时标记所有步骤为已完成
    const allIds = getTutorialConfig(gameId).steps.map(s => s.id);
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    stored[gameId] = allIds;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
    setCompleted(new Set(allIds));
  };

  const isFirstTime = completed.size === 0;

  return { completed, markCompleted, markAllCompleted, isFirstTime };
}
```

### 4.3 在游戏房间中集成

```tsx
// UndercoverRoom.tsx 或 WerewolfRoom.tsx 中
const { isFirstTime, markAllCompleted } = useTutorialProgress(gameId);

return (
  <div className="space-y-4">
    {/* 游戏主界面 */}
    {/* ... */}

    {/* 新手引导覆盖层 */}
    {isFirstTime && phase !== "WAITING" && (
      <TutorialOverlay
        steps={getTutorialConfig(gameId).steps}
        currentPhase={phase}
        isMyTurn={canSpeak || canVote}
        onComplete={markAllCompleted}
      />
    )}
  </div>
);
```

## 5. 规则百科

### 5.1 页面结构

```
/guide — 规则百科首页
/guide/:gameId — 某游戏的详细规则
/guide/:gameId/roles — 角色说明
/guide/:gameId/strategy — 策略技巧
```

### 5.2 规则数据

规则内容通过 `GameEngine.getPhaseDefinitions()` 和 `getRoleDefinitions()` 动态获取，前端渲染：

```tsx
// pages/Guide.tsx
const Guide = () => {
  const { gameId } = useParams();
  const { data: games } = useQuery({ queryKey: ['games'], queryFn: gameApi.list });

  if (!gameId) {
    // 游戏列表
    return (
      <div className="max-w-3xl mx-auto space-y-6">
        <h1 className="text-2xl font-bold">游戏规则百科</h1>
        {games?.map(game => (
          <Card key={game.id} className="cursor-pointer hover:shadow-md"
            onClick={() => navigate(`/guide/${game.id}`)}>
            <CardContent className="p-4">
              <h3 className="font-bold">{game.name}</h3>
              <p className="text-sm text-muted-foreground">{game.description}</p>
            </CardContent>
          </Card>
        ))}
      </div>
    );
  }

  // 具体游戏规则
  return (
    <div className="max-w-3xl mx-auto space-y-8">
      <h1 className="text-2xl font-bold">{getGameName(gameId)} 规则</h1>

      {/* 游戏流程 */}
      <section>
        <h2 className="text-lg font-semibold mb-3">游戏流程</h2>
        <div className="space-y-3">
          {getPhaseDefinitions(gameId).map((phase, idx) => (
            <div key={phase.phase} className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-600
                flex items-center justify-center text-sm font-bold shrink-0">
                {idx + 1}
              </div>
              <div>
                <div className="font-medium">{phase.displayName}</div>
                <div className="text-sm text-muted-foreground">{phase.description}</div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* 角色说明 */}
      <section>
        <h2 className="text-lg font-semibold mb-3">角色说明</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {getRoleDefinitions(gameId).map(role => (
            <Card key={role.role}>
              <CardContent className="p-4">
                <div className="flex items-center gap-2 mb-2">
                  <Badge className={getFactionColor(role.faction)}>
                    {role.displayName}
                  </Badge>
                  <span className="text-xs text-muted-foreground">
                    {role.faction === "GOOD" ? "好人阵营" : role.faction === "EVIL" ? "狼人阵营" : "中立"}
                  </span>
                </div>
                <p className="text-sm">{role.description}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      </section>

      {/* 开始练习 */}
      <div className="text-center py-4">
        <Button onClick={() => handleQuickStart(gameId, "EASY")}>
          <Gamepad2 className="h-4 w-4 mr-2" /> 开始练习（简单 AI）
        </Button>
      </div>
    </div>
  );
};
```

## 6. 练习模式

练习模式复用快速匹配（模块 06），但增加以下特殊处理：

- AI 难度固定为 EASY
- 不记录排名和统计
- 游戏过程中显示额外提示（如"这个玩家的描述和你的词差异很大，可能是卧底"）

```java
// QuickMatchService 中
public QuickStartResponse quickStartPractice(String gameId, User user, String playerIdHeader) {
    QuickStartRequest request = new QuickStartRequest(
        DEFAULT_PLAYER_COUNT.get(gameId), "EASY", Map.of("practice", true));
    return quickStart(gameId, user, playerIdHeader, request);
}
```

在 `finishGame` 中检查练习模式标记，跳过统计记录：

```java
if (!Boolean.TRUE.equals(state.getData().get("practice"))) {
    statsService.recordResult(...);
}
```

## 7. 上下文提示（Contextual Tooltips）

对于非首次玩家，在特定场景下显示简短提示：

```typescript
const contextualTips: Record<string, string> = {
  "undercover:DESCRIPTION:first_round": "第一轮描述建议保守一些，先观察其他人",
  "werewolf:NIGHT:seer": "作为预言家，建议先查验发言最可疑的玩家",
  "werewolf:DAY_DISCUSS:first_day": "第一天信息较少，可以先听听其他人的看法",
};
```

## 8. 数据库变更

无。引导进度存储在客户端 `localStorage` 中。规则数据通过 `GameEngine` 接口动态提供。

## 9. 通用性设计

新游戏只需：
1. 在 `GameEngine` 中实现 `getPhaseDefinitions()` 和 `getRoleDefinitions()`
2. 创建一个 `TutorialConfig` 配置对象
3. 在 `tutorialRegistry` 中注册

框架代码（`TutorialOverlay`、`useTutorialProgress`、`Guide` 页面）无需修改。

## 10. 测试要点

- [ ] 首次进入游戏时教程自动触发
- [ ] 教程步骤按阶段正确切换
- [ ] 高亮目标元素定位准确
- [ ] "跳过教程"正确标记所有步骤为已完成
- [ ] 第二次进入不再显示教程
- [ ] 规则百科页面内容正确
- [ ] 练习模式不影响排名
- [ ] 不同屏幕尺寸下的提示框定位
