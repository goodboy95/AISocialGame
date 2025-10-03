# 步骤三交付总结：谁是卧底玩法与 AI 接入

本文记录步骤三的核心成果，帮助新成员快速了解“谁是卧底”模式的实现方式与后续扩展方向。

## 1. 玩法与会话框架

- 引入 `apps/gamecore/engine.py` 定义通用 `BaseGameEngine`、`EnginePhase`、`GameEvent`。
- `GameSession` 模型扩展引擎标识、状态 JSON、当前阶段/玩家、开始/结束时间，支持多次开局与历史保留。
- `apps/gamecore/services.py` 负责启动/更新会话、广播 `game.event`，并提供引擎注册表机制方便新增玩法。

## 2. “谁是卧底”引擎

- `apps/games/models.WordPair` 作为词库来源，可按主题、难度随机抽词。
- `apps/games/undercover/engine.UndercoverEngine` 完成身份发牌、发言轮转、投票计票、平票重投与胜负判定。
- `apps/ai/services.UndercoverAIStrategy` 提供基础 AI 发言/投票策略与昵称生成，`rooms/services.start_room` 会根据房间配置自动补位。

## 3. WebSocket 与房间服务

- `apps/rooms/consumers.RoomConsumer` 新增 `game.event` 处理逻辑，将 `ready`、`submit_speech`、`submit_vote` 等事件传递给游戏引擎。
- `apps/rooms/services.start_room` 在开局时补齐 AI、创建游戏会话并广播房间快照；`_serialize_room` 会附带 `game_session` 数据。

## 4. 前端面板

- `src/store/rooms.ts` 扩展 `gameSession` 状态、`sendGameEvent` 方法与 `game.event` 处理逻辑，收到广播后自动刷新房间详情。
- `src/pages/RoomPage.vue` 重构为游戏面板：展示身份与词语、阶段提示、发言记录、投票按钮，并联动 WebSocket 触发事件。
- `src/types/rooms.ts` 新增 `GameSessionSnapshot`、`UndercoverStateView` 等类型，确保交互逻辑具备类型约束。

## 5. 测试与运行

- `apps/gamecore/tests/test_undercover_engine.py` 覆盖完整一轮发言+投票流程；房间 REST/WS 测试同步适配新字段。
- 建议执行：
  ```bash
  cd backend
  pip install -r requirements/dev.txt
  pytest
  ```
- 词库为空时请通过 Django shell 创建 `WordPair` 记录后再开启游戏。

## 6. 后续建议

- 在 `apps/gamecore.services.ENGINE_REGISTRY` 注册新的玩法引擎即可扩展更多游戏模式。
- 将 `apps/ai/services.py` 的策略替换为真实大模型调用，并在前端展示 AI 行为的流式输出。
- 引入倒计时、回合复盘、更多游戏内系统提示以及管理端词库维护工具。
