# 模块 18：动效与动画规范

> 优先级：P1 | 类型：前端动效设计 | 依赖：模块 15（设计系统） | 被依赖：模块 03、04、10、16

## 1. 设计目标

定义统一的动效语言，让游戏体验流畅、有节奏感。动效服务于信息传达，而非装饰。

原则：
- **有意义**：每个动效都传达状态变化或引导注意力
- **快速**：日常交互 ≤ 200ms，阶段切换 ≤ 500ms，戏剧性揭示 ≤ 1000ms
- **一致**：相同类型的动效使用相同的时长和缓动
- **可关闭**：尊重 `prefers-reduced-motion`

## 2. 时长与缓动

### 2.1 时长层级

| 层级 | 时长 | 用途 |
|------|------|------|
| Instant | 100ms | 按钮状态、hover、focus |
| Fast | 200ms | 下拉展开、tooltip、toast 出现 |
| Normal | 300ms | 面板滑入、卡片翻转、列表项进入 |
| Slow | 500ms | 阶段切换过渡、背景渐变 |
| Dramatic | 800-1000ms | 身份揭示、胜利动画、淘汰效果 |

### 2.2 缓动函数

```typescript
// utils/easings.ts
export const easings = {
  // 标准交互 — 快出慢入
  standard: [0.4, 0, 0.2, 1],
  // 进入 — 减速
  enter: [0, 0, 0.2, 1],
  // 退出 — 加速
  exit: [0.4, 0, 1, 1],
  // 弹性 — 用于强调
  bounce: [0.34, 1.56, 0.64, 1],
  // 戏剧性 — 用于揭示
  dramatic: [0.16, 1, 0.3, 1],
} as const;
```

对应 CSS：

```css
:root {
  --ease-standard: cubic-bezier(0.4, 0, 0.2, 1);
  --ease-enter: cubic-bezier(0, 0, 0.2, 1);
  --ease-exit: cubic-bezier(0.4, 0, 1, 1);
  --ease-bounce: cubic-bezier(0.34, 1.56, 0.64, 1);
  --ease-dramatic: cubic-bezier(0.16, 1, 0.3, 1);
}
```

## 3. 基础交互动效

### 3.1 按钮

| 状态 | 效果 | CSS |
|------|------|-----|
| Hover | 轻微上移 + 阴影增强 | `hover:-translate-y-0.5 hover:shadow-md transition-all duration-100` |
| Active | 缩小 | `active:scale-95 transition-transform duration-100` |
| Loading | 内容替换为 spinner | `<Loader2 className="animate-spin" />` |
| Disabled | 降低透明度 | `disabled:opacity-50` |

### 3.2 卡片

| 状态 | 效果 |
|------|------|
| 进入视口 | 从下方淡入 `opacity-0 translate-y-4 → opacity-100 translate-y-0` (300ms) |
| Hover | 阴影增强 + 微上移 `hover:shadow-md hover:-translate-y-1` (200ms) |
| 选中 | 边框高亮 + 缩放 `ring-2 ring-primary scale-[1.02]` (200ms) |

### 3.3 列表项

```tsx
// 列表项交错进入动画
const listItemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: (i: number) => ({
    opacity: 1, y: 0,
    transition: { delay: i * 0.05, duration: 0.3, ease: easings.enter },
  }),
};

// 使用
{items.map((item, i) => (
  <motion.div key={item.id} custom={i}
    initial="hidden" animate="visible" variants={listItemVariants}>
    <ItemCard item={item} />
  </motion.div>
))}
```

### 3.4 Toast / 通知

| 动作 | 效果 |
|------|------|
| 进入 | 从右侧滑入 + 淡入 (300ms, ease-enter) |
| 停留 | 静止 3-5 秒 |
| 退出 | 向右滑出 + 淡出 (200ms, ease-exit) |

## 4. 阶段切换动效

### 4.1 阶段过渡 Overlay

阶段切换时显示全屏过渡层，持续 1-2 秒：

```tsx
// components/game/PhaseTransition.tsx
const PhaseTransition = ({ fromPhase, toPhase, onComplete }: Props) => {
  const theme = phaseThemes[toPhase];

  return (
    <motion.div
      className="fixed inset-0 z-50 flex items-center justify-center"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.3 }}>

      {/* 背景 */}
      <motion.div className={cn("absolute inset-0", theme.bg)}
        initial={{ scale: 0, borderRadius: "50%" }}
        animate={{ scale: 1.5, borderRadius: "0%" }}
        transition={{ duration: 0.6, ease: easings.dramatic }} />

      {/* 阶段名称 */}
      <motion.div className="relative z-10 text-center"
        initial={{ scale: 0.5, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ delay: 0.3, duration: 0.4, ease: easings.bounce }}>
        <PhaseIcon phase={toPhase} className="h-16 w-16 mx-auto mb-4 text-white" />
        <h2 className="text-3xl font-bold text-white">{getPhaseName(toPhase)}</h2>
        <p className="text-white/70 mt-2">{getPhaseHint(toPhase)}</p>
      </motion.div>
    </motion.div>
  );
};
```

### 4.2 各阶段切换特效

| 切换 | 视觉效果 | 时长 |
|------|----------|------|
| WAITING → DESCRIPTION | 蓝色圆形扩展，"描述阶段"文字弹入 | 1.5s |
| DESCRIPTION → VOTING | 琥珀色从底部升起，"投票开始"文字 | 1.5s |
| DAY_DISCUSS → DAY_VOTE | 同上 | 1.5s |
| DAY → NIGHT | 暗幕从上方落下，月亮升起，星星闪烁 | 2s |
| NIGHT → DAY | 光线从右侧扫过，太阳升起 | 2s |
| * → SETTLEMENT | 金色粒子汇聚，"游戏结束"文字 | 2s |

### 4.3 夜晚→白天 特效

```tsx
const NightToDayTransition = ({ onComplete }: Props) => (
  <motion.div className="fixed inset-0 z-50 overflow-hidden"
    onAnimationComplete={onComplete}>
    {/* 夜幕 */}
    <motion.div className="absolute inset-0 bg-slate-900"
      animate={{ opacity: [1, 0] }}
      transition={{ duration: 1.5, delay: 0.5 }} />

    {/* 晨光 */}
    <motion.div className="absolute inset-0 bg-gradient-to-r from-amber-200 to-blue-300"
      initial={{ x: "-100%" }}
      animate={{ x: "0%" }}
      transition={{ duration: 1.2, ease: easings.standard }} />

    {/* 文字 */}
    <motion.div className="absolute inset-0 flex items-center justify-center"
      initial={{ opacity: 0 }}
      animate={{ opacity: [0, 1, 1, 0] }}
      transition={{ duration: 2, times: [0, 0.3, 0.7, 1] }}>
      <div className="text-center">
        <Sun className="h-12 w-12 text-amber-500 mx-auto mb-3" />
        <h2 className="text-2xl font-bold text-slate-800">天亮了</h2>
      </div>
    </motion.div>
  </motion.div>
);
```

## 5. 投票动效

### 5.1 投票选择

| 动作 | 效果 |
|------|------|
| 点击头像选中 | 头像放大 110% + 琥珀环脉冲 (200ms, bounce) |
| 切换目标 | 旧目标缩回 + 新目标放大 (200ms) |
| 确认投票 | 头像弹跳 + 投票飞行动画 (500ms) |

### 5.2 投票飞行动画

投票确认后，一个小图标从我的位置飞向目标：

```tsx
const VoteFlyAnimation = ({ from, to, onComplete }: Props) => (
  <motion.div
    className="fixed z-50 pointer-events-none"
    initial={{ x: from.x, y: from.y, scale: 1, opacity: 1 }}
    animate={{ x: to.x, y: to.y, scale: 0.5, opacity: 0.8 }}
    transition={{ duration: 0.5, ease: easings.standard }}
    onAnimationComplete={onComplete}>
    <ThumbsDown className="h-6 w-6 text-amber-500" />
  </motion.div>
);
```

### 5.3 投票计数更新

新投票到达时，目标头像上的计数气泡动画：

```tsx
// 计数变化时的弹跳
<motion.div
  key={voteCount}  // key 变化触发重新动画
  initial={{ scale: 1.5 }}
  animate={{ scale: 1 }}
  transition={{ duration: 0.3, ease: easings.bounce }}
  className="bg-red-500 text-white rounded-full ...">
  {voteCount}
</motion.div>
```

### 5.4 投票结果揭示

投票结束后的结果展示序列：

1. 所有投票线同时显示（从投票者指向目标），300ms
2. 票数柱状图从左到右依次增长，每条 200ms
3. 最高票者高亮闪烁，500ms
4. 淘汰动画（见 §6）

## 6. 淘汰动效

### 6.1 淘汰序列

被淘汰玩家的动画序列（总时长约 2s）：

```tsx
const EliminationAnimation = ({ player, onComplete }: Props) => (
  <AnimatePresence>
    {/* 1. 红色闪烁 */}
    <motion.div className="absolute inset-0 bg-red-500/30 rounded-full"
      animate={{ opacity: [0, 0.5, 0, 0.5, 0] }}
      transition={{ duration: 0.6 }} />

    {/* 2. 头像灰度化 + 缩小 */}
    <motion.div
      animate={{ scale: [1, 1.1, 0.8], filter: ["grayscale(0)", "grayscale(0)", "grayscale(1)"] }}
      transition={{ duration: 0.8, delay: 0.6 }} />

    {/* 3. X 标记淡入 */}
    <motion.div
      initial={{ scale: 0, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ delay: 1.2, duration: 0.3, ease: easings.bounce }}>
      <X className="h-8 w-8 text-white" />
    </motion.div>
  </AnimatePresence>
);
```

### 6.2 身份揭示（结算阶段）

翻牌效果，逐个揭示玩家身份：

```tsx
const IdentityReveal = ({ player, delay }: Props) => (
  <motion.div
    className="perspective-1000"
    initial={{ rotateY: 0 }}
    animate={{ rotateY: 180 }}
    transition={{ delay, duration: 0.6, ease: easings.dramatic }}>

    {/* 正面：问号 */}
    <div className="backface-hidden">
      <Card className="w-24 h-32 flex items-center justify-center bg-slate-200">
        <HelpCircle className="h-8 w-8 text-slate-400" />
      </Card>
    </div>

    {/* 背面：身份 */}
    <div className="backface-hidden rotate-y-180 absolute inset-0">
      <Card className={cn("w-24 h-32 flex flex-col items-center justify-center",
        getRoleCardBg(player.role))}>
        <Avatar className="h-12 w-12 mb-1">
          <AvatarImage src={player.avatar} />
        </Avatar>
        <span className="text-xs font-bold">{player.displayName}</span>
        <Badge className="text-[10px] mt-1">{getRoleDisplayName(player.role)}</Badge>
      </Card>
    </div>
  </motion.div>
);

// 结算页面中逐个翻牌
{players.map((player, idx) => (
  <IdentityReveal key={player.playerId} player={player} delay={idx * 0.3} />
))}
```

## 7. 倒计时动效

### 7.1 常规倒计时

圆环进度条 + 数字：

```tsx
const CountdownTimer = ({ endsAt }: Props) => {
  const remaining = useCountdown(endsAt);
  const total = useTotalDuration(endsAt);
  const progress = remaining / total;
  const isUrgent = remaining <= 10;
  const isCritical = remaining <= 5;

  return (
    <div className="relative inline-flex items-center justify-center">
      {/* SVG 圆环 */}
      <svg className="h-12 w-12 -rotate-90">
        <circle cx="24" cy="24" r="20" fill="none" strokeWidth="3"
          className="stroke-slate-200" />
        <motion.circle cx="24" cy="24" r="20" fill="none" strokeWidth="3"
          strokeDasharray={125.6}
          animate={{ strokeDashoffset: 125.6 * (1 - progress) }}
          className={cn(
            isCritical ? "stroke-red-500" :
            isUrgent ? "stroke-amber-500" : "stroke-blue-500"
          )} />
      </svg>

      {/* 数字 */}
      <motion.span
        className={cn(
          "absolute font-mono font-bold text-sm",
          isCritical && "text-red-500",
          isUrgent && !isCritical && "text-amber-500"
        )}
        animate={isCritical ? { scale: [1, 1.2, 1] } : {}}
        transition={{ duration: 0.5, repeat: Infinity }}>
        {remaining}
      </motion.span>
    </div>
  );
};
```

### 7.2 紧迫感层级

| 剩余时间 | 视觉效果 |
|----------|----------|
| > 10s | 蓝色圆环，平稳 |
| 5-10s | 琥珀色圆环，数字轻微放大 |
| ≤ 5s | 红色圆环，数字脉冲跳动，背景微红闪烁 |
| 0s | 圆环消失，"时间到"文字弹出 |

### 7.3 最后 5 秒特效

```tsx
// 最后 5 秒，每秒一次屏幕边缘红色闪烁
{isCritical && (
  <motion.div
    className="fixed inset-0 pointer-events-none z-30
      border-4 border-red-500/0 rounded-lg"
    animate={{ borderColor: ["rgba(239,68,68,0)", "rgba(239,68,68,0.3)", "rgba(239,68,68,0)"] }}
    transition={{ duration: 1, repeat: Infinity }}
  />
)}
```

## 8. 微交互

### 8.1 发言提交

| 步骤 | 效果 |
|------|------|
| 输入中 | 输入框底部蓝色进度条（字数/上限） |
| 提交 | 输入框内容向上飞出，变为聊天气泡 |
| 成功 | 气泡从底部弹入日志区 |

### 8.2 玩家加入/离开

```tsx
// 新玩家入座
<motion.div
  initial={{ scale: 0, opacity: 0 }}
  animate={{ scale: 1, opacity: 1 }}
  transition={{ duration: 0.4, ease: easings.bounce }}>
  <PlayerSeat player={newPlayer} />
</motion.div>

// 玩家离开
<motion.div
  exit={{ scale: 0, opacity: 0 }}
  transition={{ duration: 0.3, ease: easings.exit }}>
  <PlayerSeat player={leavingPlayer} />
</motion.div>
```

### 8.3 成就解锁

```tsx
const AchievementUnlockToast = ({ achievement }: Props) => (
  <motion.div
    initial={{ y: -100, opacity: 0, scale: 0.8 }}
    animate={{ y: 0, opacity: 1, scale: 1 }}
    exit={{ y: -50, opacity: 0 }}
    transition={{ duration: 0.5, ease: easings.bounce }}
    className="bg-gradient-to-r from-amber-500 to-yellow-500 text-white
      rounded-xl p-4 shadow-xl flex items-center gap-3">
    <Award className="h-8 w-8" />
    <div>
      <div className="text-xs opacity-80">成就解锁</div>
      <div className="font-bold">{achievement.name}</div>
    </div>
  </motion.div>
);
```

### 8.4 快捷表情

表情从发送者位置飘出，放大后淡出：

```tsx
const FloatingEmoji = ({ emoji, startPosition }: Props) => (
  <motion.div
    className="fixed pointer-events-none z-50 text-3xl"
    initial={{ ...startPosition, scale: 0.5, opacity: 1 }}
    animate={{
      y: startPosition.y - 100,
      scale: 1.5,
      opacity: 0,
    }}
    transition={{ duration: 1.2, ease: easings.exit }}>
    {emoji}
  </motion.div>
);
```

## 9. 页面切换

### 9.1 路由过渡

```tsx
// App.tsx 中使用 AnimatePresence
<AnimatePresence mode="wait">
  <motion.div key={location.pathname}
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: -10 }}
    transition={{ duration: 0.2, ease: easings.standard }}>
    <Routes location={location}>
      {/* ... */}
    </Routes>
  </motion.div>
</AnimatePresence>
```

### 9.2 游戏房间进入

进入游戏房间时的特殊过渡（比普通页面更戏剧化）：

```tsx
const GameRoomEntry = ({ children }: Props) => (
  <motion.div
    initial={{ scale: 0.95, opacity: 0 }}
    animate={{ scale: 1, opacity: 1 }}
    transition={{ duration: 0.5, ease: easings.dramatic }}>
    {children}
  </motion.div>
);
```

## 10. 性能指南

### 10.1 动画性能原则

- 只动画 `transform` 和 `opacity`（GPU 加速属性）
- 避免动画 `width`、`height`、`top`、`left`（触发重排）
- 复杂粒子效果使用 `<canvas>` 而非 DOM 元素
- 列表动画使用 `will-change: transform` 提示浏览器
- 移动端减少同时运行的动画数量

### 10.2 framer-motion 最佳实践

```tsx
// 使用 layout 动画代替手动计算位置
<motion.div layout layoutId={`player-${id}`}>
  <PlayerSeat />
</motion.div>

// 使用 AnimatePresence 处理退出动画
<AnimatePresence mode="popLayout">
  {items.map(item => (
    <motion.div key={item.id}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}>
      {item.content}
    </motion.div>
  ))}
</AnimatePresence>
```

### 10.3 减弱动效模式

```typescript
// hooks/useReducedMotion.ts
export function useReducedMotion() {
  const [reduced, setReduced] = useState(
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const handler = (e: MediaQueryListEvent) => setReduced(e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);
  return reduced;
}

// 使用
const reduced = useReducedMotion();
const transition = reduced
  ? { duration: 0 }
  : { duration: 0.5, ease: easings.dramatic };
```

## 11. 动效清单总览

| 场景 | 动效 | 时长 | 缓动 | 优先级 |
|------|------|------|------|--------|
| 按钮 hover | 上移+阴影 | 100ms | standard | P0 |
| 卡片进入 | 淡入上移 | 300ms | enter | P0 |
| 阶段切换 | 全屏过渡 | 1.5-2s | dramatic | P0 |
| 投票选择 | 放大+环 | 200ms | bounce | P0 |
| 投票飞行 | 图标飞行 | 500ms | standard | P1 |
| 淘汰效果 | 闪烁+灰度 | 2s | — | P0 |
| 身份翻牌 | 3D翻转 | 600ms | dramatic | P0 |
| 倒计时脉冲 | 数字跳动 | 500ms | bounce | P0 |
| 发言气泡 | 弹入 | 300ms | bounce | P1 |
| 成就解锁 | 顶部滑入 | 500ms | bounce | P1 |
| 表情飘出 | 上飘淡出 | 1.2s | exit | P2 |
| 夜晚→白天 | 光线扫过 | 1.5s | standard | P1 |
| 路由切换 | 淡入上移 | 200ms | standard | P2 |

## 12. 测试要点

- [ ] 所有动效在 60fps 下流畅运行
- [ ] `prefers-reduced-motion` 下动效正确禁用
- [ ] 阶段切换过渡不阻塞用户操作
- [ ] 翻牌动画在不同卡片数量下正确排列
- [ ] 倒计时紧迫感层级正确切换
- [ ] 移动端动效性能可接受
- [ ] 投票飞行动画起止位置准确
