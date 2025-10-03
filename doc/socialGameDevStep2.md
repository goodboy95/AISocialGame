# AI 社交游戏平台开发方案 — 步骤二：房间系统与实时通信基建

## 1. 目标概述
在完成基础框架后，构建房间管理、玩家入离场逻辑与核心实时通信能力。确保用户可以通过 REST 创建/加入房间，并通过 Channels WebSocket 在房间内进行聊天与事件广播，为接入具体游戏引擎提供可用的实时场景。

## 2. 范围与成果物
- 完整的房间管理 REST API（创建、列表、加入、退出、解散）。
- 玩家与房间的关系模型、房间状态字段更新机制。
- WebSocket 连接与分组逻辑：按房间号分发消息，处理连接鉴权。
- 基础聊天室功能：文本消息广播、系统通知。
- 前端大厅、房间页与聊天面板联调实现。

## 3. 详细任务拆分
### 3.1 数据模型与业务逻辑
1. 完善 `GameRoom` 模型字段：`status`、`owner`、`max_players`、`current_round`、`phase`、`config`、`created_at/updated_at` 等。
2. 新增 `RoomPlayer` 模型：
   - 关联用户、房间、座位序号、是否为房主、是否为 AI 等信息。
   - 扩展属性：准备接入游戏时的 `role`、`word`、`is_alive` 等字段（可后续补充）。
3. 房间服务层：
   - 创建房间时校验人数上限、默认配置。
   - 加入房间时处理并发（事务或唯一约束），防止超员。
   - 退出房间、解散房间逻辑，处理房主转移或房间关闭。
4. 补充房间列表分页/筛选、房间详情查询接口。

### 3.2 REST API
1. 在 `apps/rooms` 中实现视图：
   - `POST /api/rooms/`：创建房间。
   - `GET /api/rooms/`：获取大厅房间列表。
   - `POST /api/rooms/{id}/join/`：加入房间（输入房号或 ID）。
   - `POST /api/rooms/{id}/leave/`：退出房间。
   - `POST /api/rooms/{id}/start/`：房主触发开始游戏（后续步骤使用）。
2. 编写序列化器，确保仅返回必要字段。敏感信息（私密配置）在此阶段隐藏。
3. 完成权限校验：仅房主可解散或开始房间。

### 3.3 WebSocket 与 Channels
1. 在 `apps/rooms/consumers.py` 中实现房间 Consumer：
   - `connect`：校验用户登录状态、是否为房间成员；加入 `room_{id}` 分组。
   - `disconnect`：从分组移除；处理异常断线（可触发心跳）。
   - `receive_json`：处理消息类型（聊天、系统请求）。
2. 定义消息协议：
   - `chat.message`：包含发送者、内容、时间。
   - `system.broadcast`：房间成员变更、房主操作等。
3. 集成 Redis Channel Layer，验证多实例可扩展性。
4. 在后端提供接口或信号，当房间成员变更时通过 Channels 广播。

### 3.4 前端联调
1. **大厅页**：
   - 使用 REST 列表接口展示房间卡片，支持搜索/筛选。
   - 创建房间弹窗，提交后跳转房间详情。
2. **房间页**：
   - 展示成员列表、座位状态、AI 占位提示。
   - 提供邀请链接/房号复制功能。
   - 房主可见“开始游戏”“踢人”“添加 AI 占位（占位按钮，后续实现）”等入口。
3. **聊天面板**：
   - 集成 WebSocket 客户端；展示系统消息、聊天消息。
   - 支持消息输入、发送、历史滚动。
4. 处理断线重连与错误提示（如房间满员、被移出）。

### 3.5 测试与监控
1. 编写后端单元测试：房间创建/加入/退出接口、WS 连接鉴权、消息广播。
2. 使用 `pytest` + Channels 测试工具模拟多连接。
3. 前端使用 `vitest` 或 `cypress` 编写基础交互测试（可选）。
4. 日志与监控：在 Channels 中记录连接、断线事件；为后续排查问题提供数据。

## 4. 关键技术点与实现建议
- **并发控制**：使用数据库层唯一约束 + `select_for_update`，避免多用户同时加入同一座位导致的冲突。
- **消息格式**：统一定义 `type`、`payload` 字段，保持与后续游戏事件兼容。
- **鉴权策略**：WebSocket 握手时通过 JWT 或会话 cookie 认证，确保未登录用户无法进入房间。
- **前端状态管理**：使用 Pinia 存储房间信息，WS 消息驱动视图实时更新。
- **错误处理**：后端在 WS 中返回结构化错误，前端弹出提示并可选择重试或返回大厅。

## 5. 验收标准
- 用户可创建房间、加入/退出，REST 接口权限控制正确。
- 房间人数变化、房主操作可实时广播，成员列表实时更新。
- 聊天功能可支撑多端同时发送消息，消息顺序一致。
- 断开重连后仍可恢复房间状态。

## 6. 里程碑与时间预估
- **第 3 周**：完成后端房间模型、REST API 与单元测试。
- **第 4 周上旬**：完成 Channels 房间 Consumer 与聊天功能。
- **第 4 周中旬**：前端大厅、房间、聊天联调通过；完成步骤二验收。

> 步骤二完成后，系统具备稳定的房间与实时通信能力，可进入“谁是卧底”游戏引擎的实现。

## 7. 实现情况总结（2025-10-03）

- **后端**
  - `Room` 模型扩展状态、阶段、人数上限、配置等字段；新增 `RoomPlayer` 维护座位、房主标记、AI 占位等信息，提供唯一约束防止并发冲突。
  - 新增服务层 `apps.rooms.services` 负责创建/加入/退出/解散/开始逻辑，并对接 Channels 群组广播；REST API 通过 `RoomViewSet` 暴露全量接口。
  - `RoomConsumer` 完成 JWT 鉴权、成员校验、消息协议、系统同步等逻辑；ASGI 路由绑定 `ws/rooms/<room_id>/`。
  - 引入 `pytest`、`pytest-django`、`pytest-asyncio`，提供房间 API 与 WebSocket 单元测试；新增 `config.settings.test` 以及 `CHANNEL_LAYERS` 的内存配置。

- **前端**
  - 编写 `rooms` API 与 `useRoomsStore`，统一封装大厅分页、房间详情、加入/退出/房号加入、WebSocket 消息管理。
  - 大厅页面支持搜索、状态筛选、房号加入与创建弹窗，集成房主权限提示；房间页面展示成员席位、连接状态、实时聊天输入与房主操作按钮。
  - `GameSocket` 支持获取底层实例用于消息发送，新增 `VITE_WS_BASE_URL` 环境变量并补充 `.env.example`。

- **验证与工具**
  - `pytest` 成功覆盖房间创建/加入/退出、房主权限校验、双端 WebSocket 聊天与非成员拒绝接入场景。
  - 文档与 README 更新说明房间 REST/WebSocket 接口、前端使用说明及测试方法。

> 后续开发可在此基础上聚焦玩法引擎、房内状态机以及 AI 逻辑。

## 8. 接入与验证指南

### 8.1 REST 接口速查

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/rooms/` | 创建房间，自动生成房号并将房主入座 |
| `GET` | `/api/rooms/` | 分页房间列表，支持 `search`、`status`、`is_private` 查询参数 |
| `GET` | `/api/rooms/{id}/` | 房间详情，返回成员列表、房主标识、配置信息 |
| `POST` | `/api/rooms/{id}/join/` | 当前用户加入指定房间 |
| `POST` | `/api/rooms/{id}/leave/` | 退出房间，必要时触发房主转移 |
| `POST` | `/api/rooms/join-by-code/` | 通过房号加入房间 |
| `POST` | `/api/rooms/{id}/start/` | 房主触发游戏开始（当前阶段仅广播状态） |
| `DELETE` | `/api/rooms/{id}/` | 房主解散房间，所有成员收到系统广播 |

所有接口均需携带 `Authorization: Bearer <access_token>`，用户可通过认证接口获取 JWT。

### 8.2 WebSocket 消息协议

- 连接地址：`ws://<host>/ws/rooms/<room_id>/?token=<access_token>`。
- 服务端事件：
  - `system.sync`：连接成功后推送房间快照（房间详情 + 成员列表）。
  - `system.broadcast`：房间状态变更、成员进出等通知。
  - `chat.message`：聊天消息广播，包含 `sender`、`content`、`timestamp`。
  - `error`：错误提示（如消息格式错误、权限不足）。
  - `pong`：心跳回复，回应客户端发送的 `type: "ping"`。
- 客户端需发送 `{"type": "chat.message", "payload": {"content": "..."}}` 来广播文本消息。

### 8.3 本地联调步骤

1. 启动后端（参考 `backend/README.md`）并执行 `python manage.py migrate`。
2. 启动前端 `npm run dev`，确保 `.env` 中 `VITE_API_BASE_URL`、`VITE_WS_BASE_URL` 指向后端地址。
3. 注册/登录用户，访问大厅页面 `/lobby` 创建或加入房间。
4. 打开两个浏览器窗口分别登录不同账号，验证聊天室消息可实时同步。
5. 在后端仓库执行 `pytest`，确认核心流程可通过自动化测试。

### 8.4 常见扩展点

- **自定义房间配置**：在 `Room.config` 中增加玩法配置字段后，可通过序列化器自动下发至前端。
- **系统广播类型**：扩展 `apps/rooms/services.broadcast_room_event`（或新增 helper）时，请同步更新前端 `rooms` Store 对应的消息处理逻辑。
- **断线重连**：`system.sync` 已提供房间快照，前端可在重连后刷新状态；如需补齐历史聊天记录，可在 REST 层补充消息列表接口。
