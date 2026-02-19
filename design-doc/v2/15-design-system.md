# 模块 15：设计系统规范

> 优先级：P0 | 类型：前端基础设施 | 依赖：无 | 被依赖：模块 16、17、18

## 1. 概述

本文档定义 AISocialGame 的视觉设计系统，包括色彩体系、字体排版、间距系统、组件变体、图标规范、响应式断点和无障碍指南。所有前端模块的 UI 实现应遵循本规范。

设计基调：**浅色主题 + 游戏阶段色彩渐变**。日常页面保持清爽浅色，游戏房间内根据阶段动态切换背景色调，营造沉浸感。

## 2. 色彩体系

### 2.1 基础色板（CSS Variables）

保留现有 shadcn/ui 的 HSL 变量体系，在此基础上扩展游戏专用色。

```css
:root {
  /* ── 现有基础色（保持不变） ── */
  --background: 0 0% 100%;
  --foreground: 222.2 84% 4.9%;
  --primary: 222.2 47.4% 11.2%;
  --primary-foreground: 210 40% 98%;
  --secondary: 210 40% 96.1%;
  --muted: 210 40% 96.1%;
  --muted-foreground: 215.4 16.3% 46.9%;
  --destructive: 0 84.2% 60.2%;
  --border: 214.3 31.8% 91.4%;
  --radius: 0.5rem;

  /* ── 新增：游戏语义色 ── */
  --game-good: 142 71% 45%;          /* 好人阵营 / 成功 / 存活 — 绿 */
  --game-evil: 0 84% 60%;            /* 狼人阵营 / 危险 / 淘汰 — 红 */
  --game-neutral: 45 93% 47%;        /* 中立 / 警告 / 白板 — 琥珀 */
  --game-info: 217 91% 60%;          /* 信息提示 / 链接 — 蓝 */
  --game-spectator: 270 60% 55%;     /* 观战模式 — 紫 */

  /* ── 新增：阶段背景渐变起止色 ── */
  --phase-wait-from: 210 40% 96%;    /* 等待 — 浅灰蓝 */
  --phase-wait-to: 220 30% 92%;
  --phase-speak-from: 217 80% 55%;   /* 发言/描述 — 蓝 */
  --phase-speak-to: 230 70% 40%;
  --phase-vote-from: 35 90% 55%;     /* 投票 — 琥珀→红 */
  --phase-vote-to: 0 75% 50%;
  --phase-night-from: 240 50% 15%;   /* 夜晚 — 深蓝紫 */
  --phase-night-to: 260 40% 10%;
  --phase-settle-from: 270 60% 45%;  /* 结算 — 紫 */
  --phase-settle-to: 290 50% 30%;

  /* ── 新增：玩家状态色 ── */
  --player-alive: 142 71% 45%;
  --player-dead: 0 0% 60%;
  --player-speaking: 217 91% 60%;
  --player-voted: 45 93% 47%;
  --player-ai: 270 60% 55%;
  --player-disconnected: 0 84% 60%;
}
```

### 2.2 阶段色彩映射

| 阶段 | 背景渐变 | 文字色 | 强调色 | 适用场景 |
|------|----------|--------|--------|----------|
| WAITING | `--phase-wait-*` 浅灰蓝 | `--foreground` | `--primary` | 等待开局、房间大厅 |
| DESCRIPTION / DAY_DISCUSS | `--phase-speak-*` 蓝 | 白色 | 亮蓝 | 发言、讨论阶段 |
| VOTING / DAY_VOTE | `--phase-vote-*` 琥珀→红 | 白色 | 亮黄 | 投票阶段 |
| NIGHT | `--phase-night-*` 深蓝紫 | 白色 | 月光蓝 | 狼人杀夜晚 |
| SETTLEMENT | `--phase-settle-*` 紫 | 白色 | 金色 | 结算揭秘 |

```typescript
// utils/phaseTheme.ts
export const phaseThemes: Record<string, PhaseTheme> = {
  WAITING:     { bg: "bg-gradient-to-br from-slate-50 to-slate-100", text: "text-foreground", accent: "text-primary" },
  DESCRIPTION: { bg: "bg-gradient-to-br from-blue-600 to-blue-800", text: "text-white", accent: "text-blue-200" },
  DAY_DISCUSS: { bg: "bg-gradient-to-br from-blue-600 to-blue-800", text: "text-white", accent: "text-blue-200" },
  VOTING:      { bg: "bg-gradient-to-br from-amber-500 to-red-600", text: "text-white", accent: "text-yellow-200" },
  DAY_VOTE:    { bg: "bg-gradient-to-br from-amber-500 to-red-600", text: "text-white", accent: "text-yellow-200" },
  NIGHT:       { bg: "bg-gradient-to-br from-slate-900 to-indigo-950", text: "text-white", accent: "text-blue-300" },
  SETTLEMENT:  { bg: "bg-gradient-to-br from-purple-600 to-purple-900", text: "text-white", accent: "text-yellow-300" },
};
```

### 2.3 阵营色

| 阵营 | 色值 | Tailwind | 用途 |
|------|------|----------|------|
| 好人 / 平民 | `hsl(142, 71%, 45%)` | `text-green-500` | 角色标签、胜利提示 |
| 狼人 / 卧底 | `hsl(0, 84%, 60%)` | `text-red-500` | 角色标签、击杀提示 |
| 中立 / 白板 | `hsl(45, 93%, 47%)` | `text-amber-500` | 角色标签 |
| AI 玩家 | `hsl(270, 60%, 55%)` | `text-purple-500` | AI 标识 |

## 3. 字体排版

### 3.1 字体栈

```css
:root {
  --font-sans: "Inter", "Noto Sans SC", system-ui, -apple-system, sans-serif;
  --font-mono: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
  --font-display: "Inter", "Noto Sans SC", system-ui, sans-serif;
}
```

- 正文：`--font-sans`，中英文混排优先 Inter + Noto Sans SC
- 代码/数字：`--font-mono`，用于倒计时、统计数字
- 标题/强调：`--font-display`，与正文相同字体族，通过 weight 区分

### 3.2 字号层级

| 层级 | 大小 | 行高 | 字重 | Tailwind | 用途 |
|------|------|------|------|----------|------|
| Display | 36px | 1.1 | 700 | `text-4xl font-bold` | 英雄区标题 |
| H1 | 30px | 1.2 | 700 | `text-3xl font-bold` | 页面标题 |
| H2 | 24px | 1.3 | 600 | `text-2xl font-semibold` | 区块标题 |
| H3 | 20px | 1.4 | 600 | `text-xl font-semibold` | 卡片标题 |
| Body | 16px | 1.5 | 400 | `text-base` | 正文 |
| Body-sm | 14px | 1.5 | 400 | `text-sm` | 辅助文字、描述 |
| Caption | 12px | 1.4 | 400 | `text-xs` | 标签、时间戳 |
| Tiny | 10px | 1.3 | 500 | `text-[10px] font-medium` | 徽章内文字 |

### 3.3 游戏内特殊排版

| 场景 | 样式 | 说明 |
|------|------|------|
| 倒计时数字 | `text-5xl font-mono font-bold tabular-nums` | 等宽数字，避免跳动 |
| 玩家昵称 | `text-sm font-medium truncate max-w-[80px]` | 截断过长昵称 |
| 游戏日志 | `text-sm leading-relaxed` | 宽松行高便于阅读 |
| 词语展示 | `text-lg font-bold tracking-wide` | 卧底词语突出显示 |
| 角色名称 | `text-xs font-semibold uppercase tracking-wider` | 角色标签 |

## 4. 间距系统

### 4.1 基础间距

采用 4px 基准的间距系统，与 Tailwind 默认一致：

| Token | 值 | Tailwind | 用途 |
|-------|-----|----------|------|
| `space-0` | 0px | `p-0` / `m-0` | 无间距 |
| `space-1` | 4px | `p-1` / `gap-1` | 紧凑元素间距 |
| `space-2` | 8px | `p-2` / `gap-2` | 图标与文字间距 |
| `space-3` | 12px | `p-3` / `gap-3` | 卡片内边距（紧凑） |
| `space-4` | 16px | `p-4` / `gap-4` | 卡片内边距（标准） |
| `space-6` | 24px | `p-6` / `gap-6` | 区块间距 |
| `space-8` | 32px | `p-8` / `gap-8` | 页面区块间距 |

### 4.2 布局间距规范

| 场景 | 间距 | 说明 |
|------|------|------|
| 页面顶部留白 | `pt-6` (24px) | Header 下方 |
| 页面底部留白 | `pb-24` (96px) | 移动端底部导航预留 |
| 卡片间距 | `gap-3` (12px) | 列表中卡片间距 |
| 卡片内边距 | `p-4` (16px) | 标准卡片 |
| 表单元素间距 | `space-y-4` (16px) | 表单字段间距 |
| 按钮组间距 | `gap-2` (8px) | 并排按钮 |

## 5. 圆角与阴影

### 5.1 圆角

| Token | 值 | Tailwind | 用途 |
|-------|-----|----------|------|
| `radius-sm` | 4px | `rounded-sm` | 小元素（Badge、Tag） |
| `radius-md` | 6px | `rounded-md` | 按钮、输入框 |
| `radius-lg` | 8px | `rounded-lg` | 卡片、对话框 |
| `radius-xl` | 12px | `rounded-xl` | 大卡片、浮层 |
| `radius-full` | 9999px | `rounded-full` | 头像、圆形按钮 |

### 5.2 阴影

| 层级 | Tailwind | 用途 |
|------|----------|------|
| 无阴影 | `shadow-none` | 内嵌元素 |
| 轻微 | `shadow-sm` | 卡片默认 |
| 标准 | `shadow` | 悬浮卡片 |
| 突出 | `shadow-md` | 弹出层、下拉菜单 |
| 强调 | `shadow-lg` | 模态框、教程高亮 |
| 最高 | `shadow-xl` | Toast、通知 |

## 6. 组件变体规范

### 6.1 按钮

| 变体 | Tailwind 类 | 用途 |
|------|-------------|------|
| Primary | `bg-primary text-primary-foreground` | 主要操作（开始游戏、确认投票） |
| Secondary | `bg-secondary text-secondary-foreground` | 次要操作（加入房间、添加 AI） |
| Destructive | `bg-destructive text-destructive-foreground` | 危险操作（退出房间、踢人） |
| Ghost | `hover:bg-accent hover:text-accent-foreground` | 轻量操作（观战、查看详情） |
| Outline | `border border-input bg-background` | 表单操作（弃票、取消） |
| Game-Action | `bg-gradient-to-r from-blue-500 to-blue-600 text-white shadow-md` | 游戏核心操作（发言提交、投票确认） |

按钮尺寸：

| 尺寸 | 高度 | 内边距 | 字号 | 用途 |
|------|------|--------|------|------|
| `sm` | 32px | `px-3` | 12px | 紧凑场景（卡片内、列表操作） |
| `default` | 40px | `px-4` | 14px | 标准场景 |
| `lg` | 48px | `px-6` | 16px | 突出场景（CTA、游戏开始） |
| `icon` | 40×40px | `p-2` | — | 图标按钮 |

### 6.2 卡片

| 变体 | 样式 | 用途 |
|------|------|------|
| Default | `bg-card rounded-lg border shadow-sm` | 通用卡片 |
| Interactive | `+ hover:shadow-md transition-shadow cursor-pointer` | 可点击卡片（房间列表、游戏选择） |
| Highlighted | `+ ring-2 ring-primary` | 选中状态（投票目标、当前玩家） |
| Game-Player | `rounded-xl p-3 text-center` | 游戏内玩家卡片 |
| Danger | `border-destructive/50 bg-red-50` | 警告卡片（被投票、被击杀） |

### 6.3 头像

| 尺寸 | 大小 | 用途 |
|------|------|------|
| `xs` | 24×24px | 聊天消息、列表项 |
| `sm` | 32×32px | 投票面板、好友列表 |
| `md` | 40×40px | 导航栏、评论 |
| `lg` | 56×56px | 游戏座位（移动端） |
| `xl` | 72×72px | 游戏座位（桌面端） |
| `2xl` | 96×96px | 个人主页 |

头像状态环：

```tsx
// 通过外层 ring 表示玩家状态
const avatarRing: Record<string, string> = {
  alive:        "ring-2 ring-green-400",
  dead:         "ring-2 ring-gray-300 opacity-50 grayscale",
  speaking:     "ring-2 ring-blue-400 animate-pulse",
  voted:        "ring-2 ring-amber-400",
  disconnected: "ring-2 ring-red-400 ring-dashed",
  ai:           "ring-2 ring-purple-400",
};
```

### 6.4 Badge / 标签

| 变体 | 样式 | 用途 |
|------|------|------|
| Default | `bg-secondary text-secondary-foreground` | 通用标签 |
| Good | `bg-green-100 text-green-700` | 好人阵营 |
| Evil | `bg-red-100 text-red-700` | 狼人/卧底阵营 |
| Neutral | `bg-amber-100 text-amber-700` | 中立阵营 |
| AI | `bg-purple-100 text-purple-700` | AI 玩家标识 |
| Phase | `bg-blue-100 text-blue-700` | 阶段标签 |
| Count | `bg-primary text-primary-foreground rounded-full min-w-[20px] text-center` | 数字徽章 |

## 7. 图标规范

### 7.1 图标库

使用 `lucide-react`（已集成），统一风格为线性图标。

### 7.2 游戏专用图标映射

| 概念 | 图标 | 用途 |
|------|------|------|
| 谁是卧底 | `Eye` | 游戏标识 |
| 狼人杀 | `Moon` | 游戏标识 |
| 发言 | `MessageCircle` | 发言阶段 |
| 投票 | `Vote` / `ThumbsDown` | 投票阶段 |
| 夜晚 | `Moon` | 夜晚阶段 |
| 结算 | `Trophy` | 结算阶段 |
| AI 玩家 | `Bot` | AI 标识 |
| 观战 | `Eye` | 观战入口 |
| 好友 | `Users` | 好友系统 |
| 成就 | `Award` | 成就系统 |
| 回放 | `Play` | 对局回放 |
| 聊天 | `MessageSquare` | 聊天面板 |
| 设置 | `Settings` | 房间设置 |
| 倒计时 | `Clock` | 计时器 |
| 存活 | `Heart` | 存活状态 |
| 淘汰 | `Skull` | 淘汰状态 |
| 断线 | `WifiOff` | 断线状态 |

### 7.3 图标尺寸

| 场景 | 大小 | Tailwind |
|------|------|----------|
| 按钮内 | 16px | `h-4 w-4` |
| 列表项 | 16px | `h-4 w-4` |
| 卡片标题 | 20px | `h-5 w-5` |
| 空状态 | 48px | `h-12 w-12` |
| 阶段指示 | 24px | `h-6 w-6` |

## 8. 响应式断点

### 8.1 断点定义

| 断点 | 宽度 | Tailwind | 目标设备 |
|------|------|----------|----------|
| Mobile | < 640px | 默认 | 手机竖屏 |
| Mobile-L | 640px | `sm:` | 手机横屏 / 小平板 |
| Tablet | 768px | `md:` | 平板竖屏 |
| Desktop | 1024px | `lg:` | 桌面端 |
| Desktop-L | 1280px | `xl:` | 大屏桌面 |
| Desktop-XL | 1400px | `2xl:` | 超宽屏 |

### 8.2 布局策略

| 场景 | Mobile | Tablet | Desktop |
|------|--------|--------|---------|
| 页面容器 | 全宽 `px-4` | `max-w-3xl mx-auto` | `max-w-6xl mx-auto` |
| 导航 | 底部 Tab Bar | 底部 Tab Bar | 顶部 Header |
| 游戏座位 | 横向滚动 / 半圆 | 半圆弧形 | 圆形桌面 |
| 聊天面板 | 底部抽屉 | 右侧面板 | 右侧面板 |
| 投票面板 | 全屏 Sheet | 底部 Sheet | 内嵌面板 |
| 卡片网格 | 1列 | 2列 | 3-4列 |

### 8.3 游戏房间响应式

```
Mobile (< 768px):
┌─────────────────────┐
│   阶段指示 + 倒计时   │
├─────────────────────┤
│  ← 玩家座位横向滚动 → │
├─────────────────────┤
│                     │
│    游戏操作区域       │
│  (发言/投票/行动)     │
│                     │
├─────────────────────┤
│    游戏日志/聊天      │
│   (可切换 Tab)       │
└─────────────────────┘

Desktop (≥ 1024px):
┌──────────────────────────────────────┐
│          阶段指示 + 倒计时            │
├────────────────────────┬─────────────┤
│                        │             │
│    圆形桌面座位布局      │   聊天面板   │
│    (中央为操作区域)      │   游戏日志   │
│                        │             │
├────────────────────────┴─────────────┤
│           操作栏 (发言/投票)           │
└──────────────────────────────────────┘
```

## 9. 无障碍指南

### 9.1 色彩对比度

- 正文文字与背景对比度 ≥ 4.5:1（WCAG AA）
- 大号文字（≥ 18px bold）与背景对比度 ≥ 3:1
- 游戏阶段深色背景上使用白色文字，确保对比度
- 不仅依赖颜色传达信息，同时使用图标或文字标签

### 9.2 交互可访问性

- 所有可交互元素有 `focus-visible` 样式：`focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2`
- 按钮和链接的最小点击区域：44×44px（移动端）
- 表单元素关联 `<label>`
- 模态框支持 `Escape` 关闭，焦点陷阱
- 使用 `aria-live="polite"` 通知游戏状态变更

### 9.3 动效可访问性

- 尊重 `prefers-reduced-motion` 媒体查询
- 关键信息不依赖动画传达
- 倒计时同时提供视觉和文字信息

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

## 10. 设计 Token 导出

所有设计 Token 通过 CSS Variables 定义在 `globals.css` 中，Tailwind 通过 `tailwind.config.ts` 引用。新增的游戏专用 Token 扩展方式：

```typescript
// tailwind.config.ts 扩展
extend: {
  colors: {
    game: {
      good: "hsl(var(--game-good))",
      evil: "hsl(var(--game-evil))",
      neutral: "hsl(var(--game-neutral))",
      info: "hsl(var(--game-info))",
      spectator: "hsl(var(--game-spectator))",
    },
    phase: {
      wait: "hsl(var(--phase-wait-from))",
      speak: "hsl(var(--phase-speak-from))",
      vote: "hsl(var(--phase-vote-from))",
      night: "hsl(var(--phase-night-from))",
      settle: "hsl(var(--phase-settle-from))",
    },
  },
}
```
