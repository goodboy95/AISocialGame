# 结构更新记录

## 2024-05-26 谁是卧底游戏重构起步
- 新增 `backend/src/main/java/com/aisocialgame/backend/service/game/undercover/UndercoverSessionState.java` 用于以文档约定的结构保存“谁是卧底”局面快照，包含阶段、时间线、投票历史与配置。
- 新增 `backend/src/main/java/com/aisocialgame/backend/service/game/undercover/UndercoverStage.java` 枚举，统一状态机阶段命名。
- `UndercoverGameManager` 现支持解析房间配置、随机抽取词包、为玩家写入身份，并在状态 JSON 中附加 `undercover_session` 结构，同时广播 `game.undercover.stage` 事件。
- 初始化阶段与发言阶段会根据配置设置倒计时；若配置未提供角色分布，会按照《UndercoverGameRule.md》的推荐表推导默认值。
