# 模块 04：阶段切换与倒计时系统

> 优先级：P0 | 阶段：第一阶段 | 依赖：模块 01（WebSocket） | 被依赖：模块 12（通用投票/计时器）

## 1. 背景与目标

当前阶段切换体验：顶部状态栏文字从 `"阶段：DESCRIPTION"` 变为 `"阶段：VOTING"`，没有过渡效果。倒计时只是一个静态的 `Badge` 显示秒数。玩家容易错过阶段变化，尤其在等待他人操作时注意力分散。

目标：
- 阶段切换时有明确的全屏过渡提示
- 倒计时有视觉紧迫感（颜色渐变、脉冲动画）
- 轮到自己操作时有醒目通知
- 所有效果可复用于任何回合制游戏

## 2. 阶段切换动画

### 2.1 过渡覆盖层

阶段切换时显示一个短暂的全屏覆盖层（1.5-2 秒），然后淡出：

```tsx
// components/game/PhaseTransition.tsx
interface PhaseTransitionProps {
  phase: string;
  gameType: string;
  visible: boolean;
}

const phaseConfig: Record<string, Record<string, {
  icon: string;
  title: string;
  subtitle: string;
  bgClass: string;
}>> = {
  undercover: {
    DESCRIPTION: {
      icon: "💬",
      title: "描述阶段",
      subtitle: "请用一句话描述你的词语",
      bgClass: "from-blue-600 to-blue-800",
    },
    VOTING: {
      icon: "🗳️",
      title: "投票阶段",
      subtitle: "投出你认为的卧底",
      bgClass: "from-amber-600 to-red-700",
    },
    SETTLEMENT: {
      icon: "🎭",
      title: "游戏结束",
      subtitle: "身份即将揭晓",
      bgClass: "from-purple-600 to-purple-900",
    },
  },
  werewolf: {
    NIGHT: {
      icon: "🌙",
      title: "天黑请闭眼",
      subtitle: "夜晚行动开始",
      bgClass: "from-slate-800 to-slate-950",
    },
    DAY_DISCUSS: {
      icon: "☀️",
      title: "天亮了",
      subtitle: "开始讨论",
      bgClass: "from-amber-400 to-orange-500",
    },
    DAY_VOTE: {
      icon: "⚖️",
      title: "投票放逐",
      subtitle: "选择要放逐的玩家",
      bgClass: "from-red-600 to-red-800",
    },
    SETTLEMENT: {
      icon: "⚔️",
      title: "游戏结束",
      subtitle: "胜负已分",
      bgClass: "from-purple-600 to-purple-900",
    },
  },
};

const PhaseTransition = ({ phase, gameType, visible }: PhaseTransitionProps) => {
  const config = phaseConfig[gameType]?.[phase];
  if (!config) return null;

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
          className={`fixed inset-0 z-50 flex items-center justify-center
            bg-gradient-to-b ${config.bgClass}`}
        >
          <motion.div
            initial={{ scale: 0.5, y: 20 }}
            animate={{ scale: 1, y: 0 }}
            transition={{ type: "spring", damping: 15 }}
            className="text-center text-white"
          >
            <div className="text-6xl mb-4">{config.icon}</div>
            <h1 className="text-3xl font-bold mb-2">{config.title}</h1>
            <p className="text-lg opacity-80">{config.subtitle}</p>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};
```

### 2.2 触发逻辑

```tsx
// 在游戏房间组件中
const [showTransition, setShowTransition] = useState(false);
const prevPhaseRef = useRef<string>();

useEffect(() => {
  if (phase && phase !== prevPhaseRef.current && phase !== "WAITING") {
    setShowTransition(true);
    const timer = setTimeout(() => setShowTransition(false), 2000);
    prevPhaseRef.current = phase;
    return () => clearTimeout(timer);
  }
}, [phase]);
```

## 3. 倒计时组件

### 3.1 视觉设计

倒计时分三个阶段，视觉效果递进：

| 剩余时间 | 颜色 | 效果 |
|----------|------|------|
| > 10s | 绿色 `text-green-500` | 静态显示 |
| 5-10s | 黄色 `text-amber-500` | 轻微脉冲 |
| ≤ 5s | 红色 `text-red-500` | 强烈脉冲 + 放大 |

### 3.2 组件实现

```tsx
// components/game/CountdownTimer.tsx
interface CountdownTimerProps {
  phaseEndsAt: string | null;
  onTimeout?: () => void;
  size?: "sm" | "md" | "lg";
}

const CountdownTimer = ({ phaseEndsAt, onTimeout, size = "md" }: CountdownTimerProps) => {
  const [timeLeft, setTimeLeft] = useState(0);

  useEffect(() => {
    if (!phaseEndsAt) { setTimeLeft(0); return; }

    const update = () => {
      const diff = Math.max(0,
        Math.floor((new Date(phaseEndsAt).getTime() - Date.now()) / 1000));
      setTimeLeft(diff);
      if (diff === 0 && onTimeout) onTimeout();
    };

    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, [phaseEndsAt]);

  if (timeLeft <= 0) return null;

  const urgency = timeLeft <= 5 ? "critical" : timeLeft <= 10 ? "warning" : "normal";

  const colorClass = {
    normal: "text-green-600 border-green-200 bg-green-50",
    warning: "text-amber-600 border-amber-200 bg-amber-50",
    critical: "text-red-600 border-red-200 bg-red-50",
  }[urgency];

  const sizeClass = {
    sm: "text-sm px-2 py-0.5",
    md: "text-base px-3 py-1",
    lg: "text-2xl px-4 py-2 font-bold",
  }[size];

  return (
    <motion.div
      animate={urgency === "critical" ? {
        scale: [1, 1.1, 1],
      } : urgency === "warning" ? {
        scale: [1, 1.03, 1],
      } : {}}
      transition={{
        duration: urgency === "critical" ? 0.5 : 1,
        repeat: Infinity,
      }}
      className={`inline-flex items-center gap-1.5 rounded-full border
        font-mono ${colorClass} ${sizeClass}`}
    >
      <Timer className="h-4 w-4" />
      <span>{timeLeft}s</span>
    </motion.div>
  );
};
```

### 3.3 环形进度条变体

对于更沉浸的场景（如夜晚阶段），提供环形倒计时：

```tsx
// components/game/CircularCountdown.tsx
const CircularCountdown = ({ timeLeft, totalTime }: {
  timeLeft: number;
  totalTime: number;
}) => {
  const progress = totalTime > 0 ? timeLeft / totalTime : 0;
  const circumference = 2 * Math.PI * 45; // r=45
  const strokeDashoffset = circumference * (1 - progress);

  const color = timeLeft <= 5 ? "#ef4444" : timeLeft <= 10 ? "#f59e0b" : "#22c55e";

  return (
    <div className="relative w-28 h-28">
      <svg className="w-full h-full -rotate-90" viewBox="0 0 100 100">
        <circle cx="50" cy="50" r="45" fill="none"
          stroke="currentColor" strokeWidth="4" className="text-slate-200" />
        <circle cx="50" cy="50" r="45" fill="none"
          stroke={color} strokeWidth="4"
          strokeDasharray={circumference}
          strokeDashoffset={strokeDashoffset}
          strokeLinecap="round"
          className="transition-all duration-1000 ease-linear" />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="text-2xl font-bold" style={{ color }}>
          {timeLeft}
        </span>
      </div>
    </div>
  );
};
```

## 4. 操作提醒系统

### 4.1 "轮到你了"通知

```tsx
// components/game/TurnNotification.tsx
const TurnNotification = ({ isMyTurn, actionType }: {
  isMyTurn: boolean;
  actionType: string; // "speak" | "vote" | "night-action"
}) => {
  const messages = {
    speak: "轮到你发言了",
    vote: "请投出你的一票",
    "night-action": "请执行夜晚行动",
  };

  return (
    <AnimatePresence>
      {isMyTurn && (
        <motion.div
          initial={{ y: -50, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -50, opacity: 0 }}
          className="fixed top-4 left-1/2 -translate-x-1/2 z-40
            bg-blue-600 text-white px-6 py-3 rounded-full shadow-lg
            flex items-center gap-2"
        >
          <motion.div
            animate={{ scale: [1, 1.2, 1] }}
            transition={{ repeat: Infinity, duration: 1.5 }}
          >
            <Bell className="h-5 w-5" />
          </motion.div>
          <span className="font-medium">{messages[actionType]}</span>
        </motion.div>
      )}
    </AnimatePresence>
  );
};
```

### 4.2 浏览器通知

当页面不在前台时，使用 Notification API 提醒：

```typescript
// utils/notification.ts
export function requestNotificationPermission() {
  if ("Notification" in window && Notification.permission === "default") {
    Notification.requestPermission();
  }
}

export function sendTurnNotification(message: string) {
  if (document.hidden && Notification.permission === "granted") {
    new Notification("AI 社交游戏", {
      body: message,
      icon: "/favicon.ico",
      tag: "turn-notification", // 防止重复通知
    });
  }
}
```

### 4.3 标题栏闪烁

```typescript
// utils/titleFlash.ts
let flashInterval: number | null = null;
const originalTitle = document.title;

export function startTitleFlash(message: string) {
  if (flashInterval) return;
  let show = true;
  flashInterval = window.setInterval(() => {
    document.title = show ? `【${message}】` : originalTitle;
    show = !show;
  }, 1000);
}

export function stopTitleFlash() {
  if (flashInterval) {
    clearInterval(flashInterval);
    flashInterval = null;
    document.title = originalTitle;
  }
}
```

## 5. 玩家状态指示器

在玩家列表中增加实时状态指示：

```tsx
// 玩家卡片中的状态指示
const PlayerStatusIndicator = ({ player, phase, currentSeat, votes }: Props) => {
  const isSpeaking = phase === "DAY_DISCUSS" && currentSeat === player.seatNumber;
  const hasVoted = votes?.[player.playerId] != null;
  const isThinking = phase === "NIGHT" && /* 该角色需要行动 */;

  return (
    <div className="flex items-center gap-1">
      {isSpeaking && (
        <motion.div animate={{ opacity: [0.5, 1, 0.5] }}
          transition={{ repeat: Infinity, duration: 1.5 }}
          className="flex gap-0.5">
          {[0, 1, 2].map(i => (
            <motion.div key={i}
              animate={{ height: ["4px", "12px", "4px"] }}
              transition={{ repeat: Infinity, duration: 0.8, delay: i * 0.15 }}
              className="w-1 bg-blue-500 rounded-full" />
          ))}
        </motion.div>
      )}
      {hasVoted && <CheckCircle className="h-4 w-4 text-green-500" />}
      {isThinking && (
        <motion.div animate={{ rotate: 360 }}
          transition={{ repeat: Infinity, duration: 2, ease: "linear" }}>
          <Loader2 className="h-4 w-4 text-blue-400" />
        </motion.div>
      )}
    </div>
  );
};
```

## 6. 通用性设计

所有组件通过 props 配置，不硬编码游戏类型：

```typescript
// 阶段配置注册表（新游戏只需添加配置）
interface PhaseConfig {
  icon: string;
  title: string;
  subtitle: string;
  bgClass: string;
  defaultDuration: number;  // 默认倒计时秒数
}

// 注册新游戏的阶段配置
const gamePhaseRegistry: Record<string, Record<string, PhaseConfig>> = {};

export function registerGamePhases(gameType: string, phases: Record<string, PhaseConfig>) {
  gamePhaseRegistry[gameType] = phases;
}

// 初始化时注册
registerGamePhases("undercover", { DESCRIPTION: {...}, VOTING: {...}, ... });
registerGamePhases("werewolf", { NIGHT: {...}, DAY_DISCUSS: {...}, ... });
// 未来新游戏只需调用 registerGamePhases
```

## 7. 依赖变更

```json
{
  "framer-motion": "^11.0.0"
}
```

与模块 03 共享，不重复引入。

## 8. 测试要点

- [ ] 阶段切换动画在快速连续切换时不重叠
- [ ] 倒计时精度（与服务端时间偏差 < 1s）
- [ ] 倒计时颜色/动画在阈值边界正确切换
- [ ] 浏览器通知权限请求与发送
- [ ] 标题栏闪烁在页面回到前台时停止
- [ ] 移动端触觉反馈（如支持 Vibration API）
- [ ] 不同屏幕尺寸下的过渡动画表现
