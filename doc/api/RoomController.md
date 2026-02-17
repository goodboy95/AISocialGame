# RoomController 接口说明

## 简介
- 职责：房间创建、查询、入座与 AI 补位。
- 鉴权要求：创建/入座可带 `X-Auth-Token`，游客模式可不带 token。
- 基础路径：`/api/games/{gameId}/rooms`

## 接口列表

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/` | 按玩法查询房间列表 |
| POST | `/` | 创建房间 |
| GET | `/{roomId}` | 查询房间详情 |
| POST | `/{roomId}/join` | 玩家入座 |
| POST | `/{roomId}/ai` | 添加 AI 补位 |

## 接口详情

### GET `/` - 房间列表

**用途**：按玩法返回房间数组。

**返回值（核心字段）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | 房间 ID |
| status | String | WAITING/PLAYING |
| maxPlayers | Integer | 最大人数 |
| seats | Array | 座位列表 |

### POST `/` - 创建房间

**用途**：创建新房间。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| roomName | String | 是 | 房间名 |
| isPrivate | Boolean | 是 | 是否私密房间 |
| password | String | 否 | 私密房间密码 |
| commMode | String | 否 | 沟通模式 |
| config | Object | 否 | 玩法配置 |

**返回值**
- 201：`RoomResponse`

### GET `/{roomId}` - 房间详情

**用途**：返回房间基础信息和座位信息。

### POST `/{roomId}/join` - 玩家入座

**用途**：登录用户或游客入座，并返回 `selfPlayerId` 供后续重连与动作提交。

**Headers**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| X-Auth-Token | String | 否 | 登录 token |
| X-Player-Id | String | 否 | 断线重连时复用的玩家 ID |

**Body**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| displayName | String | 条件必填 | 游客昵称（无 token 时必填） |

**返回值**
- 200：`RoomResponse`（包含 `selfPlayerId`）

**错误码/常见错误**

| 错误码 | 说明 |
|---|---|
| 400 | 房间已满、缺少昵称 |

**WS 联动**
- 成功后推送座位事件到 `/topic/room/{roomId}/seat`：
  - 人类入座：`type=JOIN`

### POST `/{roomId}/ai` - 添加 AI

**用途**：按 `personaId` 增加一个 AI 座位。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| personaId | String | 是 | AI 人设 ID |

**返回值**
- 200：`RoomResponse`

**WS 联动**
- 成功后推送座位事件到 `/topic/room/{roomId}/seat`：
  - AI 补位：`type=AI_ADDED`
