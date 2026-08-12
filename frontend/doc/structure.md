# AISocialGame 前端结构与路由

> 更新时间：2026-08-11

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
- 路由入口位于 `frontend/src/App.tsx`（admin）与 `frontend/src/UserApp.tsx`（用户）。
- E2E 位于 `frontend/tests/`。
- 单元测试位于 `frontend/src/**/__tests__/`（vitest，`pnpm test:unit`）。

## 国际化（i18n）

- 实现位于 `frontend/src/i18n/`：`config.ts`（i18next 初始化与 Locale 类型）、`resources.ts`（zh-CN / zh-TW / en 三语完整资源）、`LanguageSelector.tsx`（header 语言选择器）、`__tests__/i18n.test.ts`。
- 默认及 fallback 语言为 `zh-CN`；持久化键 `aienie.user.locale.v1`；切换时同步更新 `html.lang` 与 `document.title`。
- 仅使用内联资源，不引入 http backend / language detector，不读取 Accept-Language。
- `main.tsx` 按路径精确分支：`/admin` 及 `/admin/*` 动态加载 `App.tsx`（维持原简中流程，不包含任何 i18n 代码）；其余路由动态加载 `UserApp.tsx`（用户路由整体由 I18nextProvider 包裹）。admin 侧 404 使用 `pages/admin/AdminNotFound.tsx`（无 i18n），用户侧 404 使用 `pages/NotFound.tsx`（随语言渲染）。

### 接入范围与映射约定

- 用户端页面/组件均已接入 `t()`：首页/大厅/房间列表/创建房间/个人中心/钱包、社区、AI 对话、排行榜、成就、回放列表与播放器、观战、百科/新手引导、404、SSO 回调，及全部 `pages/games/**`（Werewolf / Undercover / TurtleSoup 房间与 shared 运行时）、`components/social/**`（好友面板、快速匹配）、`components/wallet/**`、`components/game/**`（结算、阶段过渡、聊天、连接状态）。
- 游戏目录展示文案（name/desc/tags）与创建房间板子配置（configSchema 字段/选项 label）：`config/games.ts` 通过稳定 `game.id`/`field.id` 枚举 `GAME_DISPLAY_KEYS`、`GAME_CONFIG_KEYS` 映射到 t key；组件侧解析见 `i18n/gameTexts.ts`（`gameName` / `gameDescription` / `gameTags` / `gameFieldLabel` / `gameOptionLabel`），未知 id 回退原始数据。
- 后端 raw 中文错误：`i18n/errors.ts`（`localizeErrorMessage`）先按已知中文模式映射到 `errors.*` 本地化通用文案；未命中时 zh-CN 透出原始消息、zh-TW/en 使用调用方 fallback key。`services/api.ts` 不引入 i18n（admin 共享模块），流式请求失败等 raw 消息由调用方经 `localizeErrorMessage` 兜底。
- 用户昵称、玩家/AI 内容（发言、AI 人设、后端题目等）、品牌名不翻译；`config/games.ts` 与 `services/v2Social.ts` 中保留的中文为 zh-CN 兜底数据。

## 认证与登录说明

- 前端不再提供本地账号注册/登录页。
- 登录和注册统一由 user-service SSO 页面承接。
- 前端只负责：
  - 生成一次性 `state`
  - 跳转 `/api/auth/sso/login|register`
  - 在 `/sso/callback` 校验 `state` 并调用 `/api/auth/sso-callback`
