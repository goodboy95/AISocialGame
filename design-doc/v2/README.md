# AISocialGame v2 设计文档

> 本目录包含 AISocialGame 平台 v2 版本的全部设计文档，覆盖游戏体验优化、社交留存、架构重构、前端视觉交互四大方向，共 18 个模块。

## 项目现状

v1.x 已实现：
- 两个核心玩法：谁是卧底、狼人杀
- 房间系统、AI 补位、SSO 认证、钱包体系
- 社区动态、排行榜、管理后台
- 技术栈：Spring Boot 3 + React 18 + MySQL + Redis + gRPC

v2 要解决的核心问题：
- 游戏流畅度不足（2 秒轮询延迟、阶段切换无感知）
- AI 行为缺乏智能（随机投票、无上下文发言）
- 社交粘性弱（无好友、无成就、无回放）
- 扩展性差（新游戏需要大量 if-else 分支）

## 模块索引

### 第一阶段 — 核心体验打磨（P0）

| # | 模块 | 文档 | 说明 |
|---|------|------|------|
| 01 | WebSocket 实时通信 | [01-websocket-realtime.md](01-websocket-realtime.md) | 替换轮询，实现毫秒级状态推送 |
| 02 | AI 决策框架 | [02-ai-decision-framework.md](02-ai-decision-framework.md) | 上下文感知的 AI 发言与投票 |
| 03 | 游戏结算与大揭秘 | [03-game-settlement-reveal.md](03-game-settlement-reveal.md) | 身份翻牌、事件回顾、个人战报 |
| 04 | 阶段切换与倒计时 | [04-phase-transition-countdown.md](04-phase-transition-countdown.md) | 过渡动画、视觉紧迫感、操作提醒 |
| 05 | 断线重连与 AI 托管 | [05-reconnection-ai-takeover.md](05-reconnection-ai-takeover.md) | 自动重连、状态同步、离线 AI 接管 |

### 第二阶段 — 社交与留存（P1）

| # | 模块 | 文档 | 说明 |
|---|------|------|------|
| 06 | 快速匹配 | [06-quick-match.md](06-quick-match.md) | 一键开局、匹配队列、AI 补位 |
| 07 | 好友系统 | [07-friend-system.md](07-friend-system.md) | 好友关系、在线状态、游戏邀请 |
| 08 | 成就系统 | [08-achievement-system.md](08-achievement-system.md) | 事件驱动的成就解锁与奖励 |
| 09 | 对局回放 | [09-game-replay.md](09-game-replay.md) | 结构化事件记录、回放播放器 |
| 10 | 房间聊天与快捷表情 | [10-room-chat-reactions.md](10-room-chat-reactions.md) | 实时聊天、表情反应、快捷短语 |

### 第三阶段 — 扩展性重构（P2）

| # | 模块 | 文档 | 说明 |
|---|------|------|------|
| 11 | GameEngine 抽象层 | [11-game-engine-abstraction.md](11-game-engine-abstraction.md) | 插件化游戏引擎，新游戏零框架改动 |
| 12 | 通用投票引擎与计时器 | [12-voting-engine-phase-timer.md](12-voting-engine-phase-timer.md) | 可复用的投票和阶段管理组件 |
| 13 | 观战系统 | [13-spectator-system.md](13-spectator-system.md) | 上帝视角/跟随视角实时观战 |
| 14 | 新手引导框架 | [14-onboarding-tutorial.md](14-onboarding-tutorial.md) | 交互式教程、规则百科、练习模式 |

### 前端设计规范

| # | 模块 | 文档 | 说明 |
|---|------|------|------|
| 15 | 设计系统规范 | [15-design-system.md](15-design-system.md) | 色彩、字体、间距、组件变体、响应式断点 |
| 16 | 游戏房间 UI 重设计 | [16-game-room-ui.md](16-game-room-ui.md) | 圆形桌面布局、点击头像交互、阶段视觉变换 |
| 17 | 全局导航与布局 | [17-global-navigation.md](17-global-navigation.md) | Header/TabBar 重组、页面布局规范、好友面板 |
| 18 | 动效与动画规范 | [18-animation-motion.md](18-animation-motion.md) | 缓动函数、阶段切换、投票动效、倒计时、微交互 |

## 模块依赖关系

```
01-WebSocket ◄─────┬── 03-结算揭秘
                    ├── 04-阶段切换
                    ├── 05-断线重连
                    ├── 06-快速匹配
                    ├── 07-好友系统
                    ├── 10-房间聊天
                    └── 13-观战系统

02-AI 决策 ◄────── 11-GameEngine 抽象层

03-结算揭秘 ◄───── 09-对局回放

12-投票/计时器 ◄── 11-GameEngine 抽象层

11-GameEngine ◄─── 14-新手引导

15-设计系统 ◄─────┬── 16-游戏房间 UI
                    ├── 17-全局导航
                    └── 18-动效规范

16-游戏房间 UI ◄── 18-动效规范
```

关键路径：**01 → 03/04/05 → 06/07/10 → 11/12**

模块 02（AI 决策）和 08（成就）无前置依赖，可与第一阶段并行开发。

前端设计规范（15-18）独立于后端模块，可在任意阶段参考实施。建议在开发各功能模块的前端部分时，对照设计规范文档。

## 数据库变更汇总

| 模块 | 变更类型 | 说明 |
|------|----------|------|
| 02 | ALTER TABLE | `personas` 新增 `speech_style`、`strategy_style`、`difficulty_level` |
| 07 | CREATE TABLE | 新增 `friendships` 表 |
| 08 | CREATE TABLE | 新增 `achievement_definitions`、`player_achievements`、`achievement_progress` 表 |
| 09 | CREATE TABLE | 新增 `game_events`、`game_archives` 表 |
| 13 | ALTER TABLE | `rooms` 新增 `spectate_allowed` 字段 |

## 前端依赖变更汇总

| 包名 | 用途 | 引入模块 |
|------|------|----------|
| `@stomp/stompjs` | WebSocket STOMP 客户端 | 01 |
| `sockjs-client` | WebSocket SockJS 降级 | 01 |
| `framer-motion` | 动画（翻牌、过渡、气泡） | 03, 04, 10 |
| `html2canvas` | 分享战绩卡片生成 | 03 |

## 后端依赖变更汇总

| 依赖 | 用途 | 引入模块 |
|------|------|----------|
| `spring-boot-starter-websocket` | WebSocket 支持 | 01 |

## 阅读建议

- **快速了解全貌**：阅读本 README 的模块索引和依赖关系图
- **按阶段实施**：按第一 → 第二 → 第三阶段顺序阅读和实施
- **关注某个模块**：每个文档都是自包含的，包含背景、设计、前后端实现、数据库变更、测试要点
- **新游戏接入**：重点阅读模块 11（GameEngine 抽象层）和模块 14（新手引导框架）的"新游戏接入清单"
- **前端开发**：先阅读模块 15（设计系统规范）建立视觉基础，再按需参考 16（游戏房间）、17（导航布局）、18（动效规范）

## 设计原则

1. **通用优先**：所有模块设计时都考虑了未来新游戏类型的复用性
2. **渐进增强**：每个模块可独立实施，不需要一次性全部完成
3. **向后兼容**：重构模块（11、12）保留旧 API 接口，前端可渐进迁移
4. **最小侵入**：尽量通过新增代码而非修改现有代码来实现功能
