# AISocialGame 前端结构与路由

> 更新时间：2026-02-24

## 路由总览

| 页面名称 | 路由路径 | 说明 |
| :--- | :--- | :--- |
| 首页 | `/` | 游戏入口与热门玩法 |
| 房间列表 | `/game/:gameId` | 指定游戏的房间大厅 |
| 创建房间 | `/create/:gameId` | 创建并配置房间 |
| 等待室/游戏入口 | `/room/:gameId/:roomId` | 统一房间入口，按玩法分发 |
| 个人中心 | `/profile` | SSO 登录态、钱包、历史记录 |
| 社区 | `/community` | 社区帖子与互动 |
| AI 聊天 | `/ai-chat` | 对话与流式返回 |
| 排行榜 | `/rankings` | 玩家排行 |
| 成就中心 | `/achievements` | 成就解锁与进度 |
| 回放中心 | `/replays` | 对局回放列表 |
| 回放详情 | `/replay/:archiveId` | 单局回放 |
| 新手指南 | `/guide` | 规则与引导 |
| 观战页 | `/spectate/:gameId/:roomId` | 观战与评论 |
| SSO 回调页 | `/sso/callback` | 校验 `state` 并回调后端换取本地 token |
| 管理员登录 | `/admin/login` | 管理台登录入口 |
| 管理台-首页 | `/admin` | 运营总览 |
| 管理台-用户 | `/admin/users` | 用户查询与封禁管理 |
| 管理台-积分 | `/admin/billing` | 调账、冲正、迁移、兑换码 |
| 管理台-AI | `/admin/ai` | 模型列表与联调测试 |
| 管理台-集成状态 | `/admin/integration` | user/pay/ai 连通性探测 |

## 目录约束

- 业务页面位于 `frontend/src/pages/`。
- 通用组件位于 `frontend/src/components/`。
- API 封装位于 `frontend/src/services/api.ts`。
- 路由入口位于 `frontend/src/App.tsx`。
- E2E 位于 `frontend/tests/`。

## 认证与登录说明

- 前端不再提供本地账号注册/登录页。
- 登录和注册统一由 user-service SSO 页面承接。
- 前端只负责：
  - 生成一次性 `state`
  - 跳转 `/api/auth/sso/login|register`
  - 在 `/sso/callback` 校验 `state` 并调用 `/api/auth/sso-callback`
