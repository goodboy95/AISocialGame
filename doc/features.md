# 功能说明

## 用户与认证

- 注册、登录、刷新与注销接口位于 `/api/auth/` 下，使用 JWT 保护其余受限资源。`SecurityConfig` 放行注册、登录、元数据、房间列表与词库 GET 请求，确保游客也能浏览大厅。【F:backend/src/main/java/com/aisocialgame/backend/config/SecurityConfig.java†L24-L43】
- 后端在登录成功后返回 `access`、`refresh` 两类令牌，刷新令牌以 `RefreshToken` 实体持久化，可通过 `POST /api/auth/token/refresh/` 获取新的访问令牌。【F:backend/src/main/java/com/aisocialgame/backend/controller/AuthController.java†L65-L139】
- 前端 `user` Store 负责持久化 token、处理自动登录与登出，并在每次请求时附带 `Authorization` Header。【F:frontend/src/store/user.ts†L1-L164】

## 房间大厅

- `RoomController` 提供分页查询、按房号加入、创建、删除等 REST 操作。分页过滤在内存执行，返回值使用 `RoomDtos.RoomSummary` 附带房主、状态标签、人数等字段。【F:backend/src/main/java/com/aisocialgame/backend/controller/RoomController.java†L43-L155】
- 前端大厅页面 (`src/pages/lobby/LobbyPage.vue`) 通过 `rooms` Store 的 `fetchRooms`、`createRoom`、`joinRoomByCode` 方法管理搜索、筛选、创建房间弹窗，并在卡片中展示状态与当前人数。【F:frontend/src/pages/lobby/LobbyPage.vue†L1-L260】
- 创建房间时可选择私有房间与引擎类型，后端将配置序列化到 `Room.configJson`，供后续游戏引擎解析。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L46-L90】

## 房间内互动

- 房间详情接口返回 `RoomDtos.RoomDetail`，包含玩家列表、当前阶段、游戏会话快照以及房主/成员标记。前端进入房间后会通过 `RoomRealtimeClient` 建立 WebSocket 并接收 `system.sync` 与 `system.broadcast` 消息刷新状态。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L222-L364】【F:frontend/src/store/rooms.ts†L166-L330】
- 聊天系统区分公屏、私聊、阵营三种频道。后端 `RoomSocketCoordinator` 根据消息类型广播给对应成员，并为私聊构建唯一消息 ID；前端 `rooms` Store 将其追加到 `messages` 或 `directMessages` 队列，组件自动滚动到底部。【F:backend/src/main/java/com/aisocialgame/backend/realtime/RoomSocketCoordinator.java†L120-L220】【F:frontend/src/store/rooms.ts†L245-L330】
- 房主可启动游戏、踢人、添加 AI 玩家。`RoomService` 会生成默认词条、随机身份，并通过事件发布机制同步最新 `RoomDetail`，以驱动前端的阶段面板与倒计时。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L168-L364】

## 游戏引擎

- 当前支持“谁是卧底”“狼人杀”两类引擎。`RoomService` 在 `startGame` 时根据房间配置选择引擎，初始化 `GameSession` 的 `state` 字段并分配角色词条。后续投票、淘汰等操作通过 `RoomRealtimeEvents` 触发广播。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L256-L364】
- 前端根据 `gameSession.engine` 切换不同的组件：卧底模式展示词条、发言轮次与投票，狼人杀模式展示夜间技能按钮、阵营频道等。`normalizeGameSession` 会映射后端 JSON 到类型安全的视图模型，供组件直接消费。【F:frontend/src/store/rooms.ts†L93-L224】【F:frontend/src/pages/RoomPage.vue†L120-L420】

## AI 玩家

- 后端通过 `/api/meta/styles/` 暴露预置的 AI 风格，供前端下拉选择。添加 AI 玩家时，`RoomService` 将随机生成名称、席位并标记 `isAi=true`，在广播时与真实玩家一致。【F:backend/src/main/java/com/aisocialgame/backend/controller/MetaController.java†L16-L58】【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L126-L167】
- AI 的实际对话逻辑尚未接入模型，当前仅在游戏状态中预留 `aiStyle` 与发言占位。扩展时可在实时监听器中植入策略，将生成的消息通过 `RoomRealtimeEvents` 推送给房间成员。

## 词库与批量操作

- `WordPairController` 支持分页查询、单条增删改、批量导入（CSV/JSON）与导出。上传文件会交由 `WordPairService` 解析并写入数据库，导出则生成 UTF-8 CSV。【F:backend/src/main/java/com/aisocialgame/backend/controller/WordPairController.java†L21-L210】
- 前端尚未提供词库管理页面，但可通过管理员脚本或 Postman/HTTPie 调用接口维护题库。

## 部署与监控

- `docker-compose.yml` 将后端、前端分别打包成容器，默认使用 H2 内存数据库，不持久化数据。生产部署建议将数据库、静态文件与配置拆分到专用服务，并为 WebSocket 引入粘性会话或集中式协调。
- Logback 配置在 `backend/src/main/resources/logback-spring.xml`，区分 info 与 debug 输出，便于线上排查。可通过环境变量 `LOGGING_LEVEL_ROOT` 调整日志级别。
