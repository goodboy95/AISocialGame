# v2 社交留存与导航模块

> 更新时间：2026-03-04

## 模块目标

- 补齐 `design-doc/v2` 中前端侧的快速匹配、好友、成就、回放、观战、新手引导和全局导航入口。
- 在后端接口不完整场景下保证前端可用（本地降级）。

## 覆盖范围

- 快速匹配（模块 06）：`QuickMatchDialog` + 首页/房间列表一键开局。
- 好友系统前端（模块 07）：`FriendPanel`（搜索、请求、好友列表、邀请、观战入口）。
- 成就展示（模块 08）：`/achievements` 页面 + 结算时本地进度更新。
- 对局回放（模块 09）：`/replays` 列表、`/replay/:archiveId` 播放器、结算自动存档。
- 观战系统前端（模块 13）：`/spectate/:gameId/:roomId` 只读观战页。
- 新手引导（模块 14）：`/guide` 规则百科与教程，房间内 `TutorialOverlay`。
- 全局导航（模块 17）：主导航新增成就/回放/百科，顶栏加入快速开始与好友入口。

## 核心实现位置

- `frontend/src/components/social/FriendPanel.tsx`
- `frontend/src/components/social/QuickMatchDialog.tsx`
- `frontend/src/components/tutorial/TutorialOverlay.tsx`
- `frontend/src/components/game/SettlementPanel.tsx`
- `frontend/src/pages/Achievements.tsx`
- `frontend/src/pages/Replays.tsx`
- `frontend/src/pages/ReplayPlayer.tsx`
- `frontend/src/pages/Guide.tsx`
- `frontend/src/pages/SpectatorRoom.tsx`
- `frontend/src/services/v2Social.ts`
- `frontend/src/components/layout/MainLayout.tsx`
- `frontend/src/pages/games/UndercoverRoom.tsx`
- `frontend/src/pages/games/WerewolfRoom.tsx`

## 设计约束与降级策略

- 路由和导航入口严格对齐文档预期：`/achievements`、`/replays`、`/replay/:archiveId`、`/guide`、`/spectate/:gameId/:roomId`。
- 后端未提供完整 `friends/achievements/replays` API 时，由 `v2Social.ts` 提供本地存储降级，确保可操作与可回归。
- 房间结算阶段统一触发：
  - 成就进度更新
  - 回放归档写入
  - 玩家卡片“加好友”入口

## 验收说明

- 部署后由 subagent + Playwright 按真实用户路径验收，不依赖 build 脚本自动执行。
- 建议覆盖路径：
  - 首页 -> 快速匹配弹窗
  - 顶栏好友面板
  - `/achievements`
  - `/replays`
  - `/guide`
