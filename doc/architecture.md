# 项目整体架构

## 总览

AI Social Game 由一个 Spring Boot 后端与一个 Vue 3 前端组成，通过 REST API 与 WebSocket 保持实时同步。后端暴露 `/api` 命名空间下的 HTTP 接口，并在 `/ws/rooms/{id}` 下提供房间级别的实时通道；前端负责身份认证、房间大厅、房间内交互与界面呈现。

```text
├─ backend/      # Spring Boot REST + WebSocket 服务
│  ├─ controller/  # 认证、房间、词库、元数据 API
│  ├─ service/     # 领域逻辑、游戏回合模拟、WebSocket 消息
│  ├─ realtime/    # WebSocket 协调器、事件发布监听
│  ├─ security/    # JWT 认证、密码加密、过滤器
│  ├─ entity/      # 用户、房间、玩家、会话、词条等实体
│  └─ resources/   # application.yml、日志配置
├─ frontend/     # Vue 3 + Pinia 单页应用
│  ├─ api/         # 与后端交互的封装
│  ├─ store/       # Pinia 状态管理（用户、房间）
│  ├─ services/    # WebSocket 客户端、实时工具
│  ├─ pages/       # 登录/注册/大厅/房间等页面
│  ├─ components/  # UI 组件
│  └─ types/       # TypeScript 类型定义
└─ docker-compose.yml  # 编排前后端的开发容器
```

## 后端架构

### 模块划分

- `controller` 层封装 HTTP 路由，例如 `RoomController` 负责分页、加入/离开、AI 玩家、投票等操作，并与 `RoomService`、`RoomRealtimeEvents` 协调，确保 REST 与实时通知一致。【F:backend/src/main/java/com/aisocialgame/backend/controller/RoomController.java†L19-L214】
- `service` 层承担业务流程：`RoomService` 管理房间生命周期、席位排序、游戏引擎初始化，并通过 `ApplicationEventPublisher` 推送实时事件；`AuthService`、`WordPairService` 等则分别处理认证与词库维护。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L26-L221】
- `realtime` 子包包含 `RoomSocketCoordinator`、`RoomRealtimeListener` 与 `RoomSocketHandler`：协调器维护每个房间的 WebSocket 会话、广播消息与私聊；监听器订阅领域事件并将其广播给在线玩家。【F:backend/src/main/java/com/aisocialgame/backend/realtime/RoomSocketCoordinator.java†L1-L204】【F:backend/src/main/java/com/aisocialgame/backend/realtime/RoomRealtimeListener.java†L18-L126】
- `security` 组合 `JwtAuthenticationFilter`、`JwtService` 与 `SecurityConfig`，提供基于 JWT 的无状态认证，并允许匿名访问公共元数据、房间列表与词条 GET 接口。【F:backend/src/main/java/com/aisocialgame/backend/config/SecurityConfig.java†L1-L58】

### 数据模型

- 用户（`UserAccount`）与刷新令牌（`RefreshToken`）支持 JWT 登录与刷新；
- 房间（`Room`）与玩家（`RoomPlayer`）描述大厅、成员身份、AI 标记、席位顺序；
- 游戏会话（`GameSession`）以 JSON 保存当前回合状态，支持“谁是卧底”“狼人杀”等引擎；
- 词库（`WordPair`）维护题目及词条导入/导出。
这些实体由 Spring Data Repository 管理，并通过服务层进行序列化与业务校验。【F:backend/src/main/java/com/aisocialgame/backend/entity/Room.java†L1-L169】【F:backend/src/main/java/com/aisocialgame/backend/entity/GameSession.java†L1-L112】

### 实时通信

后端提供 `/ws/rooms/{roomId}` WebSocket 入口，由 `RoomSocketHandler` 解析 `token` 查询参数、验证 JWT 并注册连接。`RoomSocketCoordinator` 负责：

1. 在成员加入/离开时刷新玩家信息并广播；
2. 向单个玩家推送同步快照 (`system.sync`)，或在状态更新时广播 (`system.broadcast`)，其中负载包含 `RoomDtos.RoomDetail`；
3. 根据消息类型分发公屏聊天 (`chat.message`)、私聊 (`chat.direct`)、阵营频道 (`chat.faction`) 与游戏事件 (`game.event`)。

REST 层通过发布 `RoomRealtimeEvents` 触发监听器，确保房主操作（踢人、开始游戏、投票等）能立即同步到前端。【F:backend/src/main/java/com/aisocialgame/backend/realtime/RoomRealtimeEvents.java†L6-L103】

## 前端架构

### 页面与路由

前端基于 Vue Router 定义登录、注册、大厅 (`/lobby`)、房间 (`/rooms/:id`) 等页面。页面组件聚合多个业务区域，例如大厅卡片、房间阶段面板、聊天区等，并通过 Composition API 消费 Pinia Store。【F:frontend/src/router/index.ts†L9-L55】【F:frontend/src/pages/RoomPage.vue†L1-L420】

### 状态管理

`src/store/user.ts` 管理 JWT、当前用户信息与自动登录；`src/store/rooms.ts` 负责房间列表、当前房间详情与 WebSocket 状态，包含：

- 正常化后端返回（支持驼峰/下划线字段）；
- 建立与管理 `RoomRealtimeClient` 连接；
- 处理聊天、私聊、阵营消息与游戏阶段更新；
- 暴露 `fetchRooms`、`joinRoom`、`sendChat`、`startGame` 等方法供组件调用。【F:frontend/src/store/rooms.ts†L1-L330】

### API 与服务

`src/api` 封装 Axios 请求，统一挂载 `Authorization` Header。`src/services/realtime.ts` 会根据 `.env` 配置或页面地址推导 WebSocket 基地址，并在连接时附带 `token` 查询参数，避免多环境手动切换。异常 URL 会被忽略并记录 warning。【F:frontend/src/services/realtime.ts†L1-L72】

### UI 与样式

- 使用 Element Plus + Tailwind 风格的自定义样式，`src/styles/theme.scss` 管理主题变量；
- 组件目录包含聊天消息、倒计时、玩家卡片等可复用单元；
- 国际化采用 `src/i18n/index.ts` 提供的 `i18n` 实例，用于 Store 中的系统提示消息。

## 部署与环境

- `docker-compose.yml` 定义 `backend`（Maven 构建 + Spring Boot）与 `frontend`（Node 18 + Vite）服务，默认暴露 8000/5173 端口，适合一键启动开发环境。【F:docker-compose.yml†L1-L58】
- 后端默认使用 H2 内存数据库，可通过环境变量切换到外部数据库；前端通过 `.env` 配置 API/WS 地址。

## 关键注意事项

- 房间分页目前在内存中过滤排序，生产环境部署时应关注数据量与查询性能，必要时将逻辑迁移到数据库查询层。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L94-L114】
- WebSocket 会话依赖内存态的 `RoomSocketCoordinator`，多实例部署需引入共享状态或消息总线；
- 前端 Store 会在连接断开时清理状态，若新增事件类型需同时更新解析逻辑与类型定义。
