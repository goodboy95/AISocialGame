# 步骤四交付总结：狼人杀玩法与多引擎联动

本文记录步骤四的核心成果，帮助新成员快速了解“狼人杀”模式的实现方式与后续扩展方向。

## 1. 玩法扩展与引擎框架

- `apps/gamecore/services.ENGINE_REGISTRY` 新增 `werewolf` 引擎标识，`rooms.services.start_room` 可读取房间配置自动选择玩法。
- `RoomPlayer` 增加 `has_used_skill` 字段，角色分配时重置，女巫/预言家使用技能后由引擎同步写回数据库。
- `EnginePhase` 扩展 `night`/`day` 枚举，统一支撑多阶段昼夜切换。

## 2. 狼人杀状态机实现

- `apps/games/werewolf/engine.WerewolfEngine` 负责夜晚（狼人击杀、预言家查验、女巫解药/毒药）与白天（发言、投票）流程，内置阶段推进与胜负判定。
- `get_public_state` 根据玩家身份返回私密信息：
  - 狼人看到己方成员列表与当前夜袭目标。
  - 预言家查看历史查验记录与最近查验结果。
  - 女巫获知药水剩余情况与可操作的目标。
- 所有技能事件通过 `submit_wolf_target`、`submit_seer_target`、`submit_witch_action` WebSocket 事件统一传入。

## 3. AI 策略与自动行为

- `apps/ai.services.WerewolfAIStrategy` 提供夜间目标选择、查验、解药/投毒和白天发言/投票的启发式逻辑。
- `run_auto_actions` 支持 AI 自动完成夜间动作与白天发言/投票，未配置的真人玩家保持手动操作。

## 4. 前端多玩法面板

- `src/store/rooms.ts` 将 `gameSession.state` 泛化为联合类型，分别解析“谁是卧底”与“狼人杀”数据结构。
- `RoomPage.vue` 根据引擎动态渲染：
  - 狼人阶段展示击杀按钮与当前目标。
  - 预言家阶段提供查验操作，女巫阶段支持解药/毒药选择与跳过。
  - 白天沿用发言时间轴与投票交互，并在身份面板展示私密情报。
- 玩家列表/聊天区逻辑保持一致，实现多玩法共用 UI 骨架。

## 5. 自动化测试

- 新增 `apps/gamecore/tests/test_werewolf_engine.py`，模拟完整一夜一昼流程，验证夜间技能优先级、白天投票淘汰与身份公开逻辑。
- 现有 `pytest` 入口无需改动，执行 `pytest` 即可覆盖两种玩法核心流程。

## 6. 后续建议

- 引入倒计时与超时默认行为，保障夜间阶段不会被长时间阻塞。
- 设计战局复盘与战绩统计，沉淀玩家行为及 AI 决策轨迹。
- 逐步接入真实大模型接口，探索狼人阵营协同讨论与语音/文本双模输出。
