# 前端应用（Vue 3 + Vite + Element Plus）

该目录包含基于 Vue 3 + TypeScript 的前端工程。当前版本已实现登录/注册、房间大厅、房间内实时聊天与阶段推进，并适配“谁是卧底”与“狼人杀”两种游戏面板。

## 技术栈与依赖

- Vite 5、Vue 3、TypeScript
- Pinia 管理全局状态
- Vue Router 进行页面导航
- Element Plus + 自定义样式（`src/styles`）
- Axios 访问后端 REST API
- WebSocket (`RoomRealtimeClient`) 处理实时消息

## 可用脚本

```bash
npm install    # 安装依赖
npm run dev    # 本地开发，默认端口 5173
npm run build  # 生产构建
npm run preview  # 预览构建结果
```

> 项目暂未启用 ESLint/Vitest，如需引入请在 `package.json` 中补充脚本并更新本文档。

## 目录结构

```text
src/
  api/         # REST API 封装（axios 实例、房间/认证接口）
  components/  # 可复用 UI 组件（聊天消息、计时器、对话框等）
  i18n/        # 国际化配置，提供系统提示字符串
  pages/       # 页面级组件（登录、注册、大厅、房间）
  router/      # 路由定义与守卫
  services/    # WebSocket 客户端、实时工具
  store/       # Pinia Store（用户、房间）
  styles/      # 全局样式与 Element Plus 主题覆盖
  types/       # TypeScript 类型定义（房间、用户、游戏状态）
```

静态资源位于 `public/`，Vite 配置位于 `vite.config.ts`，已启用自动按需引入 Element Plus 组件。

## 环境变量

复制 `.env.example` 为 `.env` 并根据后端部署地址调整：

```ini
VITE_API_BASE_URL=http://socialgame.seekerhut.com/api
VITE_WS_BASE_URL=ws://socialgame.seekerhut.com/ws
```

- 若未设置 `VITE_WS_BASE_URL`，`RoomRealtimeClient` 会尝试根据 `VITE_API_BASE_URL` 或当前页面地址推导 WebSocket 入口。【F:frontend/src/services/realtime.ts†L9-L44】
- `VITE_API_BASE_URL` 会作为 axios 基础地址，并在 Pinia `user` Store 中附加 JWT Header。【F:frontend/src/api/http.ts†L1-L56】

## 状态管理

- `src/store/user.ts`：处理登录、登出、刷新当前用户、持久化 token，并在应用初始化时尝试自动登录。【F:frontend/src/store/user.ts†L1-L164】
- `src/store/rooms.ts`：维护房间列表、当前房间详情、实时消息与 WebSocket 连接状态。提供 `fetchRooms`、`createRoom`、`joinRoom`、`leaveRoom`、`startGame`、`sendChat` 等方法，并负责解析后端返回的驼峰/下划线字段。【F:frontend/src/store/rooms.ts†L1-L330】

## 页面与组件

- `pages/LoginPage.vue`、`pages/RegisterPage.vue`：账号注册与登录表单，调用 `user` Store 完成认证流程。
- `pages/lobby/LobbyPage.vue`：显示房间列表、过滤器、创建房间弹窗与房号加入入口。通过 `useRoomsStore` 拉取分页数据，并在卡片内展示状态、人数与房主信息。【F:frontend/src/pages/lobby/LobbyPage.vue†L1-L260】
- `pages/RoomPage.vue`：房间主界面，包含阶段面板、发言/技能按钮、聊天区域、成员列表等。会在挂载时建立 WebSocket 连接、订阅事件并在卸载时清理资源。【F:frontend/src/pages/RoomPage.vue†L1-L420】
- `components/chat/`：聊天气泡、频道切换、输入框等组件；`components/game/` 下包含阶段面板、倒计时、身份卡片等游戏相关组件。

## 实时通信流程

1. 登录成功后，`user` Store 持有 JWT。
2. 进入房间页时，`rooms` Store 调用 `RoomRealtimeClient.connect('/rooms/${roomId}')`，会自动携带 `token` 查询参数。【F:frontend/src/store/rooms.ts†L188-L274】
3. 后端推送 `system.sync` 与 `system.broadcast` 消息，Store 根据 `type` 分发至 `handleSystemPayload`、`handleChatPayload` 等分支，并更新 `messages`、`directMessages`、`gameSession` 状态。
4. 组件层响应状态变化刷新 UI（倒计时、发言列表、聊天区域）。断线时 `socketConnected` 会变为 `false`，页面顶部状态徽标显示为“离线”。

## 开发注意事项

- 新增 API 时请同步更新 `src/api/*` 与 `src/types/*` 中的类型定义，避免组件直接访问未定义字段。
- `rooms` Store 的 normalizer 支持驼峰/下划线字段混用，添加新字段时请扩展 `pickValue` 或 `normalize*` 函数，确保兼容老数据。【F:frontend/src/store/rooms.ts†L20-L152】
- WebSocket 未包含自动重连策略；若需要可在页面层监听 `socketConnected`，在断线后调用 `connectRoomRealtime` 重新建立连接。
- Element Plus 主题调整集中在 `src/styles/theme.scss`，避免在组件中直接写硬编码颜色。

## 测试与质量

- 目前暂无自动化测试脚本。若添加 Vitest/Cypress，请更新本文档并在 `package.json` 新增命令。
- 建议在开发时开启浏览器 DevTools WebSocket 面板，便于调试实时事件。

## 常见问题

- **WebSocket 无法连接**：确认 `.env` 中的 `VITE_WS_BASE_URL` 是否可访问，或者后端是否运行在 8000 端口；检查浏览器 Console 是否出现 4401 关闭码。
- **界面状态不同步**：请确认 `rooms` Store 中的事件处理是否覆盖新加入的消息类型，并确保前端发送的动作与后端事件名称一致。
- **多账号调试**：可通过浏览器隐身窗口或不同浏览器登录多个账号，或使用 `npm run dev -- --host` 在局域网内共享。
