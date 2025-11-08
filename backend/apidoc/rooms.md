# RoomController 与实时通道接口文档

- 基础路径：`/api/rooms`
- 认证要求：
  - `GET /api/rooms/**` 对游客开放（可不带 Token）。
  - 其余 REST 接口需携带 `Authorization: Bearer <access token>`。
- 相关 DTO：`RoomDtos`（定义于 `backend/src/main/java/com/aisocialgame/backend/dto/RoomDtos.java`）。

## GET `/api/rooms/`
- **说明**：分页查询房间列表，可按名称/状态/是否私有筛选。
- **查询参数**：
  - `search`（可选）：模糊搜索关键字。
  - `status`（可选）：房间状态，如 `waiting`、`playing` 等。
  - `is_private`（可选布尔）：是否私有。
  - `page`（默认 1）：页码。
  - `page_size`（默认 20）：每页数量。
- **返回**：`200 OK`，`PaginatedRooms` 对象：
  - `count`：总条数。
  - `next` / `previous`：分页链接（目前固定为 `null`）。
  - `results`：`RoomListItem` 数组，每项包含房主、状态、阶段、人数等概览字段。
- **逻辑要点**：控制器将页码转换为从 0 开始的分页请求，并调用 `RoomService.listRooms`。

## POST `/api/rooms/`
- **说明**：创建房间，仅限登录用户。
- **请求体**：`CreateRoomRequest`
  ```json
  {
    "name": "string",
    "max_players": 12,
    "is_private": false,
    "config": { "engine": "undercover", ... }
  }
  ```
- **返回**：
  - 成功：`200 OK`，`RoomDetail` 对象（包含玩家列表、房间配置、当前会话快照等）。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`RoomService.createRoom` 会绑定房主、生成房间码并初始化配置。

## GET `/api/rooms/{id}/`
- **说明**：获取房间详情，对游客也开放（若房间存在）。
- **路径参数**：`id`：房间 ID。
- **返回**：
  - 成功：`200 OK`，`RoomDetail`。
  - 未找到：`404 Not Found`。
- **逻辑要点**：若调用方登录，会在响应中标记 `isMember`、`isOwner`。

## POST `/api/rooms/{id}/join/`
- **说明**：以当前用户身份加入房间。
- **返回**：
  - 成功：`200 OK`，加入后的 `RoomDetail`。
  - 未登录：`401 Unauthorized`。
  - 房间不存在：`404 Not Found`。
- **逻辑要点**：`RoomService.joinRoom` 校验容量/房间状态并返回最新快照。

## POST `/api/rooms/join-by-code/`
- **说明**：使用短房间码加入房间。
- **请求体**：
  ```json
  { "code": "ABCD" }
  ```
- **返回**：与 `join` 相同。
- **逻辑要点**：通过 `RoomService.findByCode` 查找并加入。

## POST `/api/rooms/{id}/leave/`
- **说明**：当前用户退出房间。
- **返回**：
  - 成功：`200 OK`，退出后的房间详情。
  - 未登录：`401 Unauthorized`。
  - 房间不存在：`404 Not Found`。
- **逻辑要点**：`RoomService.leaveRoom` 会更新玩家状态并通知实时频道。

## POST `/api/rooms/{id}/start/`
- **说明**：房主发起游戏开始。
- **返回**：
  - 成功：`200 OK`，最新 `RoomDetail`。
  - 未登录：`401 Unauthorized`。
  - 非房主：`403 Forbidden`。
  - 房间不存在：`404 Not Found`。
- **逻辑要点**：仅房主可调用，`RoomService.startRoom` 会创建 `GameSession` 并推进阶段。

## POST `/api/rooms/{id}/add-ai/`
- **说明**：房主添加 AI 玩家。
- **请求体**：`AddAiRequest`
  ```json
  {
    "style": "storyteller",
    "display_name": "小剧本"
  }
  ```
- **返回**：
  - 成功：`200 OK`，更新后的房间详情。
  - 未登录：`401 Unauthorized`。
  - 非房主：`403 Forbidden`。
  - 房间不存在：`404 Not Found`。
- **逻辑要点**：`RoomService.addAiPlayer` 会依据 `style` 选择 AI 行为预设并插入 AI 成员。

## POST `/api/rooms/{id}/kick/`
- **说明**：房主踢出指定玩家。
- **请求体**：
  ```json
  { "player_id": 123 }
  ```
- **返回**：与 `add-ai` 类似。
- **逻辑要点**：`RoomService.kickPlayer` 校验目标属于该房间后移除。

## DELETE `/api/rooms/{id}/`
- **说明**：房主删除房间。
- **返回**：
  - 成功：`204 No Content`。
  - 未登录：`401 Unauthorized`。
  - 非房主：`403 Forbidden`。
  - 房间不存在：`404 Not Found`。
- **逻辑要点**：`RoomService.removeRoom` 删除实体并触发 `RoomRealtimeEvents.RoomRemoved`，所有实时连接会被关闭。

---

## 房间 WebSocket 通道
- **握手地址**：`ws(s)://<host>/ws/rooms/{roomId}?token=<access_token>` 或通过带有 `/api` 前缀的 `ws(s)://<host>/api/ws/rooms/{roomId}?token=<access_token>`。
- **认证**：必须在查询参数 `token` 中提供有效访问令牌。握手通过后会在会话中绑定用户身份。
- **初始推送**：连接成功后，服务器发送：
  ```json
  {
    "type": "system.sync",
    "payload": { /* RoomDetail 序列化内容 */ }
  }
  ```
- **广播消息**：房间状态变化时，所有成员将收到：
  ```json
  {
    "type": "system.broadcast",
    "payload": {
      "room": { /* RoomDetail */ },
      "timestamp": "2024-04-25T12:00:00Z",
      "event": "room.connection" | "room.disconnection" | 其他业务事件,
      "message": "string 可选",
      "actor": {
        "id": 1,
        "username": "player1",
        "display_name": "玩家一"
      }
    }
  }
  ```
- **聊天室消息**：
  - 公共频道：`type = "chat.message"`，payload 含 `id`、`content`、`timestamp`、`sender`。
  - 私聊/阵营频道：`type = "chat.direct"`，payload 额外包含 `channel`（`private`/`faction`）、`targetPlayerId` 或 `faction`。
- **游戏事件广播**：
  ```json
  {
    "type": "game.event",
    "payload": {
      "event": "string",
      "payload": { ... },
      "timestamp": "..."
    }
  }
  ```

### 客户端可发送的消息
| `type` | payload 结构 | 作用 | 后端处理逻辑 |
| --- | --- | --- | --- |
| `chat.message` | `{ "content": "string" }` | 在房间公共频道发言 | 通过 `RoomSocketCoordinator.publishPublicChat` 广播 `chat.message`。|
| `chat.private` | `{ "targetPlayerId": number, "content": "string" }` | 发送私聊 | 由 `publishPrivateChat` 向双方推送 `chat.direct` 消息。|
| `chat.faction` | `{ "content": "string", "faction": "string?" }` | 阵营频道发言 | `publishFactionChat` 广播 `chat.direct`，附带 `faction`。|
| `game.event` | `{ "event": "submit_speech" \| "update_speech_draft", "payload": { "content": "..." } }` | 游戏内操作事件 | `RoomGameEventService` 捕获并调用 `UndercoverGameManager` 相应方法；若事件未被处理则原样广播 `game.event`。|

### 断开行为
- 当房间被删除或关闭时，服务器会以关闭码 `4000` 主动断开连接。
- 若握手 token 缺失/非法，握手会直接被拒绝并返回 `401`。
