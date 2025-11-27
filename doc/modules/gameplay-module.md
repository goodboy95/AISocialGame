# 游戏流程模块（狼人杀 / 谁是卧底）

- **后端入口**：`GamePlayController` + `GamePlayService`
  - 统一的 `/state` 状态查询，返回当前阶段、轮次、座位、日志、个人身份/词语、已投票信息与夜晚待办。
  - `/start` 由房主触发，生成身份（狼人杀角色/卧底词语），房间状态切到 PLAYING。
  - `/speak` 按座位顺序推进描述/白天发言，AI 座位自动补充发言。
  - `/vote` 在投票阶段提交，所有存活玩家（含 AI）投票后自动结算并判断胜负。
  - `/night-action`（狼人杀）支持狼人刀人、预言家查验、女巫解毒/下毒，超时自动跳过缺失操作。
  - 结算时通过 `StatsService` 更新排行榜并为人类玩家发放金币奖励。
- **前端入口**：`frontend/src/pages/games/UndercoverRoom.tsx`、`.../WerewolfRoom.tsx`
  - 使用 React Query 轮询 `/state`，展示实时倒计时、座位生存状态、日志与个人身份。
  - 支持发言输入、投票选择、夜晚行动选择；AI 自动推进。
  - 加入房间时会在响应中返回 `selfPlayerId` 并缓存到 localStorage，重连或操作时随 `X-Player-Id` 传入，避免重复占座。
- **数据模型**：
  - `GameState` 持久化房间对局状态，包含 `GamePlayerState`（身份、词语、生存）、阶段、轮次、日志与阶段数据。
  - `UndercoverWordRepository` 提供卧底词对，`PlayerStats` 用于排行榜统计。
