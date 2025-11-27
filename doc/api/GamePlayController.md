# GamePlayController 接口说明

基址：`/api/games/{gameId}/rooms/{roomId}`

## GET /state
- **用途**：获取实时对局状态（卧底、狼人杀共用）。
- **请求头**：`X-Auth-Token` 或 `X-Player-Id`（游客/断线重连时使用 selfPlayerId）。
- **响应 200**：`GameStateResponse`
  - 字段：`phase`（WAITING/DESCRIPTION/VOTING/NIGHT/DAY_DISCUSS/DAY_VOTE/SETTLEMENT）、`round`、`currentSeat`、`currentSpeakerName`、`phaseEndsAt`、`players[]`（包含座位、生存状态，自己或结算时才包含 `role/word`）、`logs[]`、`myPlayerId/mySeatNumber/myWord/myRole`、`votes`（已投票映射）、`pendingAction`（狼人夜晚待办）、`extra`（卧底词语/预言家查验结果等）。

## POST /start
- **用途**：房主开局，生成身份/词语并切换到首阶段。
- **请求头**：同上。
- **响应 200**：`GameStateResponse`
- **错误**：403 非房主，400 人数不足。

## POST /speak
- **用途**：当前发言玩家提交发言并切换到下一个座位/阶段。
- **请求头**：同上。
- **请求体**
```json
{ "content": "这轮我的描述..." }
```
- **响应 200**：最新 `GameStateResponse`
- **错误**：400 非发言者/阶段错误。

## POST /vote
- **用途**：投票阶段提交投票（卧底/狼人杀共用）。
- **请求头**：同上。
- **请求体**
```json
{ "targetPlayerId": "xxx", "abstain": false }
```
- **响应 200**：最新 `GameStateResponse`
- **错误**：400 阶段错误/目标无效/重复投票。

## POST /night-action （仅狼人杀）
- **用途**：夜晚行动（狼人刀人、预言家查验、女巫解毒/下毒）。
- **请求头**：同上。
- **请求体**
```json
{ "action": "WOLF_KILL|SEER_CHECK|WITCH_SAVE|WITCH_POISON", "targetPlayerId": "xxx", "useHeal": true }
```
- **响应 200**：最新 `GameStateResponse`
- **错误**：400 角色不符/目标无效。
