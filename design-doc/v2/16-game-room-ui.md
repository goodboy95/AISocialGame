# 模块 16：游戏房间 UI 重设计

> 优先级：P1 | 类型：前端交互设计 | 依赖：模块 15（设计系统）、模块 01（WebSocket） | 被依赖：模块 04、10、13

## 1. 设计目标

将游戏房间从"功能表单"升级为"沉浸式游戏桌"。核心改动：

- 圆形桌面座位布局（桌面端）/ 半圆弧 + 横向滚动（移动端）
- 点击头像交互（投票、查看信息、夜晚操作）
- 阶段驱动的视觉变换（背景、色调、氛围）
- 聊天气泡式游戏日志
- 统一的操作区域

## 2. 座位布局

### 2.1 桌面端 — 圆形桌面

玩家围坐在虚拟圆桌周围，当前玩家固定在底部中央（6 点钟位置），其他玩家按座位号顺时针排列。

```
         [P4]    [P5]
      [P3]          [P6]
    [P2]              [P7]
      [P1]          [P8]
         [  我  ]
```

布局算法：

```typescript
// utils/seatLayout.ts
interface SeatPosition {
  x: number;  // 百分比 0-100
  y: number;  // 百分比 0-100
  angle: number;  // 旋转角度（用于名牌朝向）
}

export function calculateSeatPositions(
  totalSeats: number,
  myIndex: number,
  containerWidth: number,
  containerHeight: number
): SeatPosition[] {
  const positions: SeatPosition[] = [];
  const centerX = 50;
  const centerY = 50;
  const radiusX = 40;  // 水平半径（百分比）
  const radiusY = 38;  // 垂直半径（略扁的椭圆）

  for (let i = 0; i < totalSeats; i++) {
    // 从底部（6点钟）开始，顺时针排列
    // 当前玩家在 index 0，对应角度 90°（底部）
    const offset = (i - myIndex + totalSeats) % totalSeats;
    const angle = (90 + (offset * 360) / totalSeats) % 360;
    const rad = (angle * Math.PI) / 180;

    positions.push({
      x: centerX + radiusX * Math.cos(rad),
      y: centerY + radiusY * Math.sin(rad),
      angle: angle,
    });
  }
  return positions;
}
```

座位组件：

```tsx
// components/game/CircularTable.tsx
const CircularTable = ({ players, myPlayerId, phase, onPlayerClick }: CircularTableProps) => {
  const myIndex = players.findIndex(p => p.playerId === myPlayerId);
  const positions = calculateSeatPositions(players.length, myIndex, 600, 500);

  return (
    <div className="relative w-full aspect-[6/5] max-w-[600px] mx-auto">
      {/* 桌面背景 */}
      <div className="absolute inset-[15%] rounded-full bg-gradient-to-br from-slate-100 to-slate-200
        border-4 border-slate-200 shadow-inner" />

      {/* 中央信息区 */}
      <div className="absolute inset-[25%] flex flex-col items-center justify-center text-center">
        <PhaseIndicator phase={phase} />
        <CountdownTimer />
      </div>

      {/* 玩家座位 */}
      {players.map((player, idx) => (
        <div key={player.playerId}
          className="absolute -translate-x-1/2 -translate-y-1/2 transition-all duration-500"
          style={{ left: `${positions[idx].x}%`, top: `${positions[idx].y}%` }}>
          <PlayerSeat
            player={player}
            isMe={player.playerId === myPlayerId}
            phase={phase}
            onClick={() => onPlayerClick(player)}
          />
        </div>
      ))}
    </div>
  );
};
```

### 2.2 移动端 — 半圆弧 + 横向滚动

移动端空间有限，采用混合方案：

- 4-6 人：上方半圆弧排列对手，底部固定自己
- 7+ 人：横向滚动条，当前玩家居中高亮

```
半圆弧（4-6人）:
    [P3]  [P4]  [P5]
  [P2]              [P6]
  ─────────────────────
  │    操作区域        │
  │   [  我  ]        │
  └───────────────────┘

横向滚动（7+人）:
  ← [P2][P3][P4][P5][P6][P7][P8] →
  ─────────────────────────────────
  │         操作区域               │
  │        [  我  ]               │
  └───────────────────────────────┘
```

```tsx
// components/game/MobileSeatLayout.tsx
const MobileSeatLayout = ({ players, myPlayerId, phase, onPlayerClick }: Props) => {
  const others = players.filter(p => p.playerId !== myPlayerId);
  const me = players.find(p => p.playerId === myPlayerId);

  if (others.length <= 5) {
    // 半圆弧布局
    return (
      <div className="relative w-full h-32">
        {others.map((player, idx) => {
          const angle = Math.PI * (idx + 1) / (others.length + 1);
          const x = 50 - 40 * Math.cos(angle);
          const y = 5 + 25 * (1 - Math.sin(angle));
          return (
            <div key={player.playerId}
              className="absolute -translate-x-1/2"
              style={{ left: `${x}%`, top: `${y}%` }}>
              <PlayerSeat player={player} isMe={false} phase={phase}
                onClick={() => onPlayerClick(player)} size="sm" />
            </div>
          );
        })}
      </div>
    );
  }

  // 横向滚动
  return (
    <div className="overflow-x-auto scrollbar-hide">
      <div className="flex gap-3 px-4 py-2 min-w-max">
        {others.map(player => (
          <PlayerSeat key={player.playerId} player={player} isMe={false}
            phase={phase} onClick={() => onPlayerClick(player)} size="sm" />
        ))}
      </div>
    </div>
  );
};
```

## 3. 玩家座位卡片

### 3.1 座位卡片设计

每个座位包含：头像（带状态环）、昵称、角色标签（仅自己可见或结算后）、状态指示。

```tsx
// components/game/PlayerSeat.tsx
interface PlayerSeatProps {
  player: GamePlayerView;
  isMe: boolean;
  phase: string;
  onClick: () => void;
  size?: "sm" | "md" | "lg";
  showVoteCount?: number;
  isTargeted?: boolean;  // 被我选为投票目标
}

const sizeMap = {
  sm: { avatar: "h-12 w-12", text: "text-xs", card: "w-16" },
  md: { avatar: "h-14 w-14", text: "text-sm", card: "w-20" },
  lg: { avatar: "h-18 w-18", text: "text-sm", card: "w-24" },
};

const PlayerSeat = ({ player, isMe, phase, onClick, size = "md",
  showVoteCount, isTargeted }: PlayerSeatProps) => {
  const s = sizeMap[size];

  return (
    <button onClick={onClick}
      className={cn(
        "flex flex-col items-center gap-1 transition-all",
        s.card,
        isTargeted && "scale-110",
        !player.alive && "opacity-50",
      )}>
      {/* 头像 + 状态环 */}
      <div className="relative">
        <Avatar className={cn(s.avatar, getAvatarRing(player, phase))}>
          <AvatarImage src={player.avatar} />
          <AvatarFallback>{player.displayName[0]}</AvatarFallback>
        </Avatar>

        {/* 投票计数气泡 */}
        {showVoteCount != null && showVoteCount > 0 && (
          <div className="absolute -top-1 -right-1 bg-red-500 text-white
            rounded-full min-w-[20px] h-5 flex items-center justify-center
            text-[10px] font-bold animate-bounce-in">
            {showVoteCount}
          </div>
        )}

        {/* AI 标识 */}
        {player.isAi && (
          <div className="absolute -bottom-0.5 -right-0.5 bg-purple-500
            rounded-full p-0.5">
            <Bot className="h-2.5 w-2.5 text-white" />
          </div>
        )}

        {/* 淘汰标记 */}
        {!player.alive && (
          <div className="absolute inset-0 flex items-center justify-center
            bg-black/40 rounded-full">
            <X className="h-6 w-6 text-white" />
          </div>
        )}
      </div>

      {/* 昵称 */}
      <span className={cn(s.text, "font-medium truncate w-full text-center",
        isMe && "text-blue-600 font-bold")}>
        {isMe ? "我" : player.displayName}
      </span>

      {/* 角色标签（仅自己或结算后可见） */}
      {player.role && (isMe || phase === "SETTLEMENT") && (
        <Badge className={cn("text-[10px] px-1.5 py-0",
          getRoleBadgeColor(player.role))}>
          {getRoleDisplayName(player.role)}
        </Badge>
      )}

      {/* 选中高亮环 */}
      {isTargeted && (
        <div className="absolute inset-0 rounded-full ring-4 ring-amber-400
          animate-pulse pointer-events-none" />
      )}
    </button>
  );
};
```

### 3.2 头像状态环颜色

| 状态 | 环色 | 动效 |
|------|------|------|
| 存活（默认） | 绿色 `ring-green-400` | 无 |
| 正在发言 | 蓝色 `ring-blue-400` | `animate-pulse` |
| 已投票 | 琥珀 `ring-amber-400` | 无 |
| 被投票目标 | 红色 `ring-red-400` | `animate-pulse` |
| AI 托管 | 紫色 `ring-purple-400` | 无 |
| 断线 | 红色虚线 `ring-red-400 ring-dashed` | 无 |
| 淘汰 | 灰色 `ring-gray-300` + 灰度 | 无 |

## 4. 点击头像交互

### 4.1 交互流程

点击其他玩家头像时，根据当前阶段弹出不同的操作面板：

```typescript
const handlePlayerClick = (player: GamePlayerView) => {
  if (!player.alive || player.playerId === myPlayerId) return;

  switch (phase) {
    case "VOTING":
    case "DAY_VOTE":
      // 直接选为投票目标，再次点击确认
      setVoteTarget(player.playerId);
      break;
    case "NIGHT":
      // 选为夜晚行动目标
      setNightTarget(player.playerId);
      break;
    default:
      // 查看玩家信息弹窗
      setSelectedPlayer(player);
      break;
  }
};
```

### 4.2 投票交互

投票阶段点击头像的流程：

1. 第一次点击 → 头像高亮（琥珀环），显示"确认投票？"浮层
2. 再次点击同一头像 → 提交投票
3. 点击其他头像 → 切换目标
4. 点击空白区域 → 取消选择

```tsx
// 投票确认浮层
{voteTarget && (
  <div className="absolute z-10" style={getPopoverPosition(voteTarget)}>
    <Card className="shadow-lg border-amber-200 p-3 w-40">
      <p className="text-sm font-medium text-center mb-2">
        投票给 {getPlayerName(voteTarget)}？
      </p>
      <div className="flex gap-2">
        <Button size="sm" className="flex-1" onClick={confirmVote}>
          确认
        </Button>
        <Button size="sm" variant="ghost" onClick={() => setVoteTarget(null)}>
          取消
        </Button>
      </div>
    </Card>
  </div>
)}
```

### 4.3 夜晚行动交互

狼人杀夜晚阶段，不同角色点击头像的行为：

| 角色 | 点击效果 | 确认文案 |
|------|----------|----------|
| 狼人 | 选择击杀目标 | "击杀 {name}？" |
| 预言家 | 选择查验目标 | "查验 {name}？" |
| 女巫（毒药） | 选择毒杀目标 | "对 {name} 使用毒药？" |
| 守卫 | 选择守护目标 | "守护 {name}？" |

### 4.4 玩家信息弹窗

非操作阶段点击头像，显示玩家信息卡：

```tsx
const PlayerInfoPopover = ({ player, onClose }: Props) => (
  <Card className="w-56 shadow-xl">
    <CardContent className="p-4 text-center space-y-2">
      <Avatar className="h-16 w-16 mx-auto">
        <AvatarImage src={player.avatar} />
      </Avatar>
      <div className="font-medium">{player.displayName}</div>
      {player.isAi && <Badge variant="secondary"><Bot className="h-3 w-3 mr-1" />AI</Badge>}
      <div className="text-xs text-muted-foreground">
        座位 #{player.seatNumber}
      </div>
      {/* 游戏结束后可加好友 */}
      <Button size="sm" variant="outline" className="w-full">
        <UserPlus className="h-3 w-3 mr-1" /> 加好友
      </Button>
    </CardContent>
  </Card>
);
```

## 5. 阶段视觉变换

### 5.1 背景渐变切换

游戏房间的背景色随阶段变化，使用 CSS transition 平滑过渡：

```tsx
const GameRoom = () => {
  const theme = phaseThemes[phase] || phaseThemes.WAITING;

  return (
    <div className={cn(
      "min-h-screen transition-all duration-1000 ease-in-out",
      theme.bg
    )}>
      {/* 房间内容 */}
    </div>
  );
};
```

### 5.2 各阶段视觉特征

| 阶段 | 背景 | 氛围元素 | 操作区 |
|------|------|----------|--------|
| WAITING | 浅灰蓝渐变 | 无 | 准备按钮、座位管理 |
| DESCRIPTION | 蓝色渐变 | 发言气泡浮动 | 文字输入框 |
| VOTING | 琥珀→红渐变 | 投票计数动画 | 点击头像投票 |
| NIGHT | 深蓝紫渐变 + 星星粒子 | 月亮图标、暗角 | 角色行动面板 |
| DAY_DISCUSS | 蓝色渐变 | 太阳图标 | 文字输入框 |
| SETTLEMENT | 紫色渐变 + 金色粒子 | 翻牌动画 | 结算信息 |

### 5.3 夜晚特效

```tsx
// 夜晚阶段的氛围层
{phase === "NIGHT" && (
  <div className="fixed inset-0 pointer-events-none z-0">
    {/* 暗角 */}
    <div className="absolute inset-0 bg-radial-gradient from-transparent to-black/30" />
    {/* 月亮 */}
    <Moon className="absolute top-8 right-8 h-12 w-12 text-yellow-200 opacity-60" />
  </div>
)}
```

## 6. 游戏日志 — 聊天气泡式

### 6.1 设计理念

将游戏日志从纯文本列表改为聊天气泡样式，更直观地展示"谁说了什么"。

### 6.2 消息类型

| 类型 | 样式 | 示例 |
|------|------|------|
| 我的发言 | 右侧蓝色气泡 | "这个词和水果有关" |
| 他人发言 | 左侧灰色气泡 + 头像 | [头像] "我觉得是一种食物" |
| 系统消息 | 居中灰色文字 | "投票阶段开始" |
| 投票结果 | 居中卡片 | "张三 以 3 票被淘汰" |
| 阶段切换 | 居中分割线 + 标签 | ── 第 2 轮 描述阶段 ── |
| AI 发言 | 左侧紫色气泡 + AI 标识 | [AI] "这个东西很常见" |

```tsx
// components/game/GameLog.tsx
const GameLog = ({ logs, players, myPlayerId }: Props) => (
  <div className="space-y-2 overflow-y-auto max-h-[300px] px-3">
    {logs.map((log, idx) => {
      if (log.type === "SYSTEM") {
        return (
          <div key={idx} className="text-center text-xs text-muted-foreground py-1">
            {log.content}
          </div>
        );
      }

      if (log.type === "PHASE_CHANGE") {
        return (
          <div key={idx} className="flex items-center gap-2 py-2">
            <div className="flex-1 h-px bg-border" />
            <Badge variant="secondary" className="text-[10px]">{log.content}</Badge>
            <div className="flex-1 h-px bg-border" />
          </div>
        );
      }

      const isMe = log.playerId === myPlayerId;
      const player = players.find(p => p.playerId === log.playerId);

      return (
        <div key={idx} className={cn("flex gap-2", isMe && "flex-row-reverse")}>
          {!isMe && (
            <Avatar className="h-7 w-7 shrink-0">
              <AvatarImage src={player?.avatar} />
              <AvatarFallback className="text-[10px]">
                {player?.displayName?.[0]}
              </AvatarFallback>
            </Avatar>
          )}
          <div className={cn(
            "max-w-[70%] rounded-2xl px-3 py-2 text-sm",
            isMe
              ? "bg-blue-500 text-white rounded-br-sm"
              : "bg-slate-100 text-foreground rounded-bl-sm"
          )}>
            {!isMe && (
              <div className="text-[10px] font-medium text-muted-foreground mb-0.5">
                {player?.displayName}
                {player?.isAi && <Bot className="inline h-2.5 w-2.5 ml-1" />}
              </div>
            )}
            {log.content}
          </div>
        </div>
      );
    })}
  </div>
);
```

## 7. 操作区域

### 7.1 统一操作栏

游戏房间底部固定操作栏，根据阶段和角色动态切换内容：

```tsx
const GameActionBar = ({ phase, canSpeak, canVote, role, onSpeak, onVote }: Props) => {
  // 发言阶段
  if ((phase === "DESCRIPTION" || phase === "DAY_DISCUSS") && canSpeak) {
    return (
      <div className="fixed bottom-0 inset-x-0 bg-white/90 backdrop-blur border-t p-3
        safe-area-bottom">
        <div className="flex gap-2 max-w-lg mx-auto">
          <Input placeholder="输入你的描述..." value={text} onChange={...}
            className="flex-1" autoFocus />
          <Button onClick={() => onSpeak(text)} disabled={!text.trim()}
            className="bg-blue-500 hover:bg-blue-600 text-white">
            <Send className="h-4 w-4" />
          </Button>
        </div>
      </div>
    );
  }

  // 投票阶段 — 提示文字（实际投票通过点击头像）
  if ((phase === "VOTING" || phase === "DAY_VOTE") && canVote) {
    return (
      <div className="fixed bottom-0 inset-x-0 bg-white/90 backdrop-blur border-t p-3
        safe-area-bottom text-center">
        <p className="text-sm text-muted-foreground">点击玩家头像进行投票</p>
        <Button variant="ghost" size="sm" onClick={() => onVote("", true)}
          className="mt-1">
          弃票
        </Button>
      </div>
    );
  }

  // 等待状态
  return (
    <div className="fixed bottom-0 inset-x-0 bg-white/90 backdrop-blur border-t p-3
      safe-area-bottom text-center">
      <p className="text-sm text-muted-foreground animate-pulse">
        等待其他玩家操作...
      </p>
    </div>
  );
};
```

## 8. 房间整体布局

### 8.1 桌面端完整布局

```tsx
const GameRoomDesktop = () => (
  <div className={cn("min-h-screen flex flex-col", phaseTheme.bg)}>
    {/* 顶部：阶段指示 + 倒计时 */}
    <header className="flex items-center justify-between px-6 py-3">
      <PhaseIndicator phase={phase} />
      <CountdownTimer endsAt={phaseEndsAt} />
      <div className="flex gap-2">
        <Button variant="ghost" size="icon"><Settings /></Button>
        <Button variant="ghost" size="icon"><LogOut /></Button>
      </div>
    </header>

    {/* 主体：座位 + 聊天 */}
    <main className="flex-1 flex gap-4 px-6 pb-20">
      <div className="flex-1 flex items-center justify-center">
        <CircularTable players={players} myPlayerId={myId}
          phase={phase} onPlayerClick={handlePlayerClick} />
      </div>
      <aside className="w-80 flex flex-col bg-white/80 backdrop-blur rounded-xl border">
        <Tabs defaultValue="log">
          <TabsList className="w-full">
            <TabsTrigger value="log" className="flex-1">游戏日志</TabsTrigger>
            <TabsTrigger value="chat" className="flex-1">聊天</TabsTrigger>
          </TabsList>
          <TabsContent value="log"><GameLog logs={logs} /></TabsContent>
          <TabsContent value="chat"><ChatPanel roomId={roomId} /></TabsContent>
        </Tabs>
      </aside>
    </main>

    {/* 底部：操作栏 */}
    <GameActionBar phase={phase} canSpeak={canSpeak} canVote={canVote} />
  </div>
);
```

### 8.2 移动端完整布局

```tsx
const GameRoomMobile = () => (
  <div className={cn("min-h-screen flex flex-col", phaseTheme.bg)}>
    {/* 顶部：阶段 + 倒计时 */}
    <header className="flex items-center justify-between px-4 py-2">
      <PhaseIndicator phase={phase} compact />
      <CountdownTimer endsAt={phaseEndsAt} compact />
      <Button variant="ghost" size="icon"><MoreHorizontal /></Button>
    </header>

    {/* 对手座位区 */}
    <MobileSeatLayout players={players} myPlayerId={myId}
      phase={phase} onPlayerClick={handlePlayerClick} />

    {/* 我的信息 */}
    <div className="flex items-center justify-center gap-3 py-2">
      <PlayerSeat player={me} isMe={true} phase={phase} size="lg" />
      {myWord && (
        <div className="bg-white/90 rounded-lg px-3 py-1.5 shadow-sm">
          <span className="text-xs text-muted-foreground">我的词语</span>
          <div className="font-bold">{myWord}</div>
        </div>
      )}
    </div>

    {/* 游戏日志（可折叠） */}
    <div className="flex-1 overflow-hidden">
      <GameLog logs={logs} players={players} myPlayerId={myId} />
    </div>

    {/* 底部操作栏 */}
    <GameActionBar phase={phase} canSpeak={canSpeak} canVote={canVote} />
  </div>
);
```

## 9. 等待阶段 UI

游戏开始前的等待阶段，展示座位管理界面：

| 元素 | 说明 |
|------|------|
| 空座位 | 虚线圆圈 + "空位" 文字，点击可邀请 |
| 已入座 | 头像 + 昵称 + 准备状态 |
| AI 座位 | 紫色边框 + Bot 图标 |
| 开始按钮 | 房主可见，满足最低人数后高亮 |
| 房间信息 | 游戏类型、房间号、配置 |
| 邀请链接 | 一键复制邀请链接 |

## 10. 测试要点

- [ ] 圆形布局在 4-12 人时座位不重叠
- [ ] 移动端半圆弧 / 横向滚动切换正确
- [ ] 点击头像投票流程完整（选择→确认→提交）
- [ ] 阶段背景渐变过渡平滑
- [ ] 聊天气泡正确区分消息类型
- [ ] 操作栏根据阶段正确切换
- [ ] 淘汰玩家灰度 + 不可点击
- [ ] 投票计数气泡实时更新
- [ ] 夜晚阶段暗色主题下文字可读
