# RoomController 接口说明

基址：`/api/games/{gameId}/rooms`

## GET /
- **用途**：按游戏列出房间列表。
- **响应 200**：`RoomResponse[]`
  - 字段：`id`,`gameId`,`name`,`status (WAITING|PLAYING)`,`maxPlayers`,`isPrivate`,`commMode`,`config`,`seats[]`

## POST /
- **用途**：创建房间。
- **请求头**：`X-Auth-Token` 可选（带上则创建者入座为房主）。
- **请求体**
```json
{
  "roomName": "string",
  "isPrivate": false,
  "password": "optional",
  "commMode": "voice|text",
  "config": { "playerCount": 12, ... }   // 根据游戏 schema
}
```
- **响应 201**：`RoomResponse`

## GET /{roomId}
- **用途**：房间详情（含座位信息）。
- **响应 200**：`RoomResponse`

## POST /{roomId}/join
- **用途**：玩家入座。
- **请求头**：`X-Auth-Token` 可选；`X-Player-Id` 可选（游客重连时带上上次返回的 playerId，可避免重复占座）。
- **请求体**
```json
{ "displayName": "游客1234" }
```
- **响应 200**：更新后的 `RoomResponse`，包含 `selfPlayerId` 字段（当前入座的 playerId，后续调用游戏接口或重连时需要携带）。
- **错误**：400 房间已满 / 缺少昵称。

## POST /{roomId}/ai
- **用途**：向房间添加一名预设 AI 人设。
- **请求体**
```json
{ "personaId": "ai1" }
```
- **响应 200**：更新后的 `RoomResponse`。
- **说明**：后端会为新增 AI 随机生成昵称（可选通过配置 `ai.name.endpoint` 调用外部 AI 接口生成；失败则使用本地随机词库组合），头像依旧取自人设。
