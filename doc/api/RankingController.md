# RankingController 接口说明

基址：`/api/rankings`

## GET /
- **用途**：按游戏返回积分排行榜（来源于真实结算）。
- **查询参数**：`gameId`（可选，默认 `total`，可选值：`werewolf`、`undercover`、`total`）
- **响应 200**：`PlayerStats[]`（最多 20 条，按 score 降序）
  - 字段：`id`、`playerId`、`gameId`、`displayName`、`avatar`、`gamesPlayed`、`wins`、`score`
