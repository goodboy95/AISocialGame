# 游戏流程模块（狼人杀 / 谁是卧底）

## 模块作用
- 提供“开局 -> 阶段推进 -> 投票/夜晚行动 -> 结算”的统一对局主流程。
- 在 v2 中补充实时推送、断线状态同步、超时托管（AI takeover）与自动推进能力。

## 功能点清单
| 功能点 | 作用 | 实现位置 |
|---|---|---|
| 状态查询 `/state` | 返回当前阶段、回合、当前座位、日志、玩家私有身份信息、夜晚待办 | `backend/src/main/java/com/aisocialgame/controller/GamePlayController.java`、`backend/src/main/java/com/aisocialgame/service/GamePlayService.java` |
| 开局与私有身份下发 | 房主开局后完成角色/词语分配，并通过 WS 私有队列下发身份信息 | `backend/src/main/java/com/aisocialgame/service/GamePlayService.java`、`backend/src/main/java/com/aisocialgame/websocket/GamePushService.java` |
| 发言/投票/夜晚行动 | 处理卧底与狼人杀的阶段动作，动作后推送状态变更事件 | `backend/src/main/java/com/aisocialgame/service/GamePlayService.java` |
| 断线检测与托管 | 按连接活跃时间更新 `ONLINE/DISCONNECTED/AI_TAKEOVER`，超时后自动托管/弃票 | `backend/src/main/java/com/aisocialgame/websocket/PlayerConnectionService.java`、`backend/src/main/java/com/aisocialgame/service/GamePlayService.java` |
| 前端房间实时渲染 | 取消轮询，改为 WS 事件驱动刷新；展示倒计时、阶段过渡、连接状态、聊天面板 | `frontend/src/hooks/useGameSocket.ts`、`frontend/src/pages/games/UndercoverRoom.tsx`、`frontend/src/pages/games/WerewolfRoom.tsx`、`frontend/src/components/game/*` |

## 关键流程
1. 房主调用 `/start`，后端初始化 `GameState` 并推送 `PHASE_CHANGE`。
2. 玩家通过 `/speak`、`/vote`、`/night-action` 交互，后端保存状态并推送 `state` 事件。
3. 玩家连接状态由 STOMP 连接 + 活跃打点共同维护，断线后进入 `DISCONNECTED`，超时后进入 `AI_TAKEOVER`。
4. 阶段超时或操作完备时自动推进，结算后记录统计并回写房间状态。

## 相关文件
- `backend/src/main/java/com/aisocialgame/dto/GamePlayerView.java`：响应新增 `connectionStatus`。
- `backend/src/main/java/com/aisocialgame/model/GamePlayerState.java`：新增连接状态与活跃时间字段。
- `backend/src/main/java/com/aisocialgame/dto/ws/*`：WS 推送事件 DTO。
- `frontend/src/types/index.ts`：新增 WS 事件与聊天消息类型。
