# 前端应用（Vue 3 + Vite + Element Plus）

该目录包含基于 Vue 3 + TypeScript 的前端工程。当前版本已实现登录/注册、房间大厅、房间内实时聊天，并同时适配“谁是卧底”与“狼人杀”两种游戏面板，与后端房间 API 与 WebSocket 完成打通。

## 主要特性

- 使用 Vite 5 构建，支持热更新。
- 集成 Element Plus，启用按需自动引入（unplugin-auto-import / unplugin-vue-components）。
- 采用 Vue Router + Pinia 管理认证状态与房间大厅/房间内数据，`rooms` Store 可根据引擎自动解析不同状态结构。
- 封装房间 REST API、房号加入/创建表单以及基于 JWT 的 WebSocket 客户端。
- 大厅支持搜索、状态筛选、房号加入、创建房间弹窗；房间页可根据玩法显示身份词语/私密情报、昼夜阶段提示、发言/技能/投票操作和实时聊天。

## 可用脚本

```bash
npm install    # 安装依赖
npm run dev    # 本地开发，默认端口 5173
npm run build  # 产物构建
npm run preview  # 预览构建结果
```

## 目录结构

```text
src/
  api/         # REST API 封装
  pages/       # 登录/注册/大厅/房间页面
  router/      # 路由配置
  services/    # WebSocket 客户端
  store/       # 认证 / 房间状态管理
  styles/      # 全局样式与主题
  types/       # TypeScript 类型定义
```

## 环境变量

复制 `.env.example` 为 `.env`，并根据后端部署地址调整：

```
VITE_API_BASE_URL=http://localhost:8000/api
VITE_WS_BASE_URL=ws://localhost:8000/ws
```

## 房间模块速览

- `src/store/rooms.ts`：管理大厅分页、房间详情、WebSocket 状态、消息列表与游戏会话，公开 `fetchRooms`、`joinRoom`、`leaveRoom`、`sendChat`、`sendGameEvent` 等方法。
- `src/api/rooms.ts`：封装房间 REST 请求，包含房号加入、房间启动等接口。
- `src/pages/lobby`、`src/pages/RoomPage.vue`：大厅列表与房间游戏面板，实现身份展示、发言/技能/投票交互与聊天。
- `src/services/gameSocket.ts`：统一维护 WebSocket 连接（包含 `connect`、`disconnect`、`getInstance`），并对外抛出监听器。

## 本地调试指南

1. **配置身份**：在后端获取 JWT（参见根目录 README 中的功能验证指南），并通过登录页面输入用户名/密码登录。
2. **大厅验证**：访问 `/lobby`，使用顶部搜索框筛选房间或通过“创建房间”弹窗快速创建新房间。
3. **加入房间**：点击房间卡片或输入房号加入，房间页将展示身份、阶段提示、发言记录与系统消息面板。
4. **发起游戏**：房主点击“开始游戏”后，房间会自动补齐 AI 并进入准备阶段；可通过房间页的“通知开始发言”“提交发言”“投票”按钮触发 `game.event`，观察实时面板与聊天更新。

### 常见问题

- 连接 WebSocket 失败时，请确认 `.env` 中 `VITE_WS_BASE_URL` 与后端实际地址匹配，且访问链接包含 `token` 参数。
- 若需在开发环境模拟多用户，可打开浏览器隐身窗口或使用不同的浏览器登录不同账号。
- 当修改 API 定义后，记得同步更新 `src/types/rooms.ts` 与对应组件使用的类型提示。
