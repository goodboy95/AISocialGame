# 谁是卧底功能开发文档

> 目标：在现有 AISocialGame 项目内，实现与《UndercoverGameRule.md》一致的“谁是卧底”房间逻辑及 AI 玩家行为，确保与 Spring Boot 后端与 Vue 前端完全适配。

---

## 1. 功能范围与目标
- 支持房主在房间配置阶段选择“谁是卧底”模式，并灵活设置各身份数量、词库与回合节奏。
- 后端负责身份分发、阶段推进、发言/投票裁决等“主持人”职责，并通过 REST + WebSocket 保持前端实时同步。
- 引入具备自我推断能力的 AI 玩家：根据已有发言、投票及自身词语估计身份，输出合规发言与投票行为。
- 兼容现有房间大厅、AI 管理、实时聊天与事件广播能力，并对断线、补位、平票等边界场景提供防护。

---

## 2. 术语与角色配置

| 术语 | 说明 |
| --- | --- |
| **平民 (civilian)** | 拿到平民词语；目标是淘汰卧底与白板。 |
| **卧底 (spy)** | 拿到卧底词语；人数通常 1-2；目标是存活到终局。 |
| **白板 (blank)** | 无词语；可选角色，最多 1 人；若在卧底全出局后存活则单独胜利。 |
| **主持人逻辑** | 原真人主持人职责由后端流程自动承担。 |

### 2.1 可配置选项
房主通过房间配置面板设置以下参数，并写入 `Room.config` (JSON Map)：

```json
{
  "game_mode": "undercover",
  "role_config": {
    "civilians": 6,
    "spies": 2,
    "blank": 1
  },
  "word_pack_id": 3,
  "round_config": {
    "max_rounds": 8,
    "discussion_seconds": 90,
    "vote_seconds": 30,
    "pk_vote_seconds": 20,
    "allow_self_vote": false
  },
  "ai_behavior": {
    "style": "balanced",
    "speech_tempo": "normal"
  }
}
```

- `role_config` 会在房间开始时校验：总人数 = 平民 + 卧底 + 白板，白板≤1。
- 若房主未填写，则按照推荐表（见《UndercoverGameRule.md》）自动推导默认值。
- `word_pack_id` 关联 `WordPair` 词包；若为空则从公共词库随机抽取。
- `round_config` 控制倒计时、回合上限、是否允许自投等规则。

---

## 3. 数据模型与持久化

### 3.1 GameSession 存储结构
在 `GameSession.session_state` JSON 中添加 `undercover` 模式专用结构：

```json
{
  "stage": "DISCUSSION",
  "round": 2,
  "players": [
    {
      "player_id": 12,
      "role": "civilian",
      "word": "牛奶",
      "alive": true,
      "ai": false,
      "speeches": [
        {"round": 1, "text": "早餐常见的饮品", "timestamp": 1715151123}
      ]
    }
  ],
  "timeline": [
    {"event": "ROUND_START", "round": 1, "timestamp": "..."}
  ],
  "vote_history": [
    {
      "round": 1,
      "type": "MAIN",
      "ballots": [{"from": 12, "to": 18}],
      "result": {"eliminated": [18], "method": "majority"}
    }
  ],
  "word_pair": {"civilian": "牛奶", "spy": "豆浆"},
  "config": { ... 同房间配置 ... },
  "phase_payload": {
    "discussion_order": [12, 8, 23],
    "countdown_deadline": 1715151200,
    "pk_candidates": null
  }
}
```

存储要求：
- `role` 仅在服务端保留，前端仅获得自身信息及公开事件。
- `speeches`、`vote_history` 为 AI 推理与回放提供数据来源。
- `timeline` 用于调试与行为回放，便于 QA/监控。

### 3.2 DTO 扩展
- 向 `RoomDtos.RoomDetail` 增加 `game_session` 字段（已存在时扩展 `UndercoverSessionView`）。
- `UndercoverSessionView` 包含对前端可见的字段：当前阶段、回合号、剩余倒计时、存活玩家列表、公开发言、投票摘要。
- 提供 `RoomDtos.UndercoverSpeechCommand`、`RoomDtos.UndercoverVoteCommand` 等请求体定义。

---

## 4. 阶段状态机与主持人逻辑

### 4.1 状态机概览

| 阶段 | 主持人职责 | 倒计时 | 自动触发下一阶段条件 |
| --- | --- | --- | --- |
| `LOBBY_READY` | 校验人数、锁定配置、加载词语 | 无 | 房主点击开始 |
| `ROLE_DISTRIBUTION` | 分配角色及词语，广播个人信息 | 5s | 全部确认已同步 |
| `DISCUSSION` | 按顺序触发展言，记录文本/语音 | 配置 `discussion_seconds` | 所有人发言或倒计时结束 |
| `VOTE_MAIN` | 收集投票、处理缺投 | `vote_seconds` | 所有人投票或超时自动弃权 |
| `VOTE_PK_PREP` | 公布 PK 选手，清理投票 | 5s | 进入 PK 发言 |
| `DISCUSSION_PK` | PK 玩家重新发言 | 配置 `discussion_seconds` / 2 | PK 玩家完成发言或倒计时 |
| `VOTE_PK` | 非 PK 玩家二次投票 | `pk_vote_seconds` | 票数确定 |
| `ELIMINATION` | 公布出局身份，写入历史 | 5s | 根据剩余人员判胜或进入下一轮 |
| `GAME_END` | 发布胜方、存档、解锁重开 | 无 | 房主重新开始则回到 `LOBBY_READY` |

### 4.2 关键主持人逻辑
- **倒计时与超时处理**：服务端维护绝对截止时间，定时器到达时自动推进，并记录超时原因（用于战斗日志）。
- **发言顺序**：默认顺时针（房间加入顺序）；若有人断线，自动跳过并在他们重连后将其剩余发言机会设为 0。
- **投票校验**：禁止投自己时直接拒绝请求；未在截止前提交的玩家自动视为投给随机存活玩家（可配置 `auto_vote_strategy`）。
- **平票处理**：`VOTE_MAIN` 平票 → 设置 `pk_candidates`，进入 `DISCUSSION_PK`；PK 阶段仍平票时按配置处理（默认共同出局）。
- **胜负判定**：
  - 卧底全出局 → 若白板存活，则白板胜；否则平民胜。
  - 存活人数达到规则上限 → 卧底胜。
- **断线恢复**：`RoomSocketCoordinator` 检测断线 → 服务端保留 `phase_payload`，玩家重连后通过 `system.sync` 获取当前阶段状态及自己是否已发言/投票。

---

## 5. 后端实现要点

### 5.1 服务层职责
- `UndercoverGameEngine`（新建 service 组件）负责：
  1. 校验房间配置与当前玩家数；
  2. 生成词语/角色分配；
  3. 驱动状态机（`advanceStage`、`recordSpeech`、`submitVote` 等）；
  4. 产出 `UndercoverEvent` 发布至实时层。
- `RoomService.startRoom` 根据 `config.game_mode` 选择引擎，注入 `UndercoverGameEngine`。
- `GameSessionRepository` 负责读写 JSON；任何阶段推进前先持久化，确保崩溃恢复。

### 5.2 REST API
- `POST /rooms/{id}/undercover/speech/` → `UndercoverSpeechCommand { text, round, stage }`
- `POST /rooms/{id}/undercover/vote/` → `UndercoverVoteCommand { target_id, reason, stage }`
- `POST /rooms/{id}/undercover/timer/auto-progress/`（内部定时任务调用，不向前端暴露）
- 依赖现有认证过滤器：所有操作需带 JWT。

### 5.3 事件广播
新增消息类型：
- `game.undercover.stage`：广播阶段切换（载荷含回合号、倒计时、发言顺序、pkCandidates）。
- `game.undercover.speech`：广播公共发言文本；若是 AI，附加 `style_hint`。
- `game.undercover.vote`：在裁决后公开票型统计。
- `game.undercover.result`：宣布淘汰/胜负。

`RoomRealtimeListener` 订阅 `UndercoverEvent` 并调用 `RoomSocketCoordinator.broadcast(roomId, payload)`。

---

## 6. AI 玩家设计

### 6.1 输入与上下文
AI 通过服务端内部回调获取以下信息：
- 自身词语、身份（仅在初始化时记录，后续应依靠推断以模拟不确定性）。
- 当前回合存活玩家列表与发言顺序。
- 所有公开发言文本（含回合、发言者、时间戳）。
- 过往投票结果与被淘汰身份。
- 配置项（如是否允许自投、讨论时长等）。

### 6.2 身份概率推断
AI 针对三种身份维护概率 `P_civilian`、`P_spy`、`P_blank`：
1. **初始先验**：来自房间配置的比例；
2. **发言分析**：
   - 利用词向量或关键词匹配（本地词典）计算自己词语与他人描述的一致度；
   - 若一致度过高，降低自己为卧底的概率；
   - 若出现明显冲突词（如“液体”“固体”矛盾），提高自己为卧底或白板概率。
3. **投票反馈**：
   - 若上一轮被高票针对，增加卧底概率；
   - 若卧底被淘汰但自己仍未暴露，适度提高平民概率。
4. **白板判定**：若描述多轮后仍缺乏关键词，可保留 10%-20% 白板概率。

概率归一化后，若某角色概率 > `0.6` 即视为当前信念；否则保持“不确定”状态。

### 6.3 发言策略
- **有明确身份**：
  - 认为自己是平民 → 给出与词语高度相关但不直接暴露的描述，引用词语关联属性（颜色、用途、场景）。
  - 认为自己是卧底 → 在允许的模糊范围内靠拢平民词语，避免使用排他特性。
  - 认为自己是白板 → 使用泛化描述，多提感受、情绪或模棱两可的类别，避免具体细节。
- **身份不确定**：
  - 输出中性描述，范围覆盖多种可能；若回合>1，可引用他人描述中的交集词汇。
  - 在文本末尾附加模糊修饰（如“我觉得挺常见的”“有时候会在家里看到”）。
- 发言长度控制在 15~35 个汉字，可根据 `speech_tempo` 调整延迟。
- 发言前后由服务端注入 typing 延迟事件，增强真实感。

### 6.4 投票策略
1. 基于身份概率：
   - 认为自己是平民/白板：优先投给发言偏离词语共性的玩家；
   - 认为自己是卧底：优先投给怀疑度高的平民或对自己构成威胁的人。
2. 若信息不足：
   - 随机从除自己外的存活玩家中选择一人（使用稳定随机种子保证可重放）。
3. 平票阶段：
   - 收敛投票目标在 PK 候选内；若无法判断，再次随机但保持与前一次投票不同。

### 6.5 自适应与日志
- 每次发言/投票后更新内部信念并写入 `ai_trace`（开发模式下可查阅）。
- 当服务端判断 AI 行为异常（概率求和漂移、响应超时）时，回退到“保守策略”（模板化模糊发言 + 随机投票）。

---

## 7. 前端适配

### 7.1 页面与组件
- `RoomPage.vue` 中根据 `room.game_mode` 切换到 `UndercoverRoomPanel`。
- 新增组件：
  - `UndercoverStageTimeline`：展示当前阶段、倒计时。
  - `UndercoverSpeechList`：按回合显示发言（AI 发言标记为“🤖”）。
  - `UndercoverVotePanel`：根据阶段切换主投票/PK 投票 UI。
- 倒计时使用 Pinia Store 中的服务器时间戳计算，避免客户端漂移。

### 7.2 Store 扩展
- `rooms.store` 增加 `undercoverSession` state，字段包括：`stage`, `round`, `discussionOrder`, `pkCandidates`, `countdown`.
- Actions：
  - `submitUndercoverSpeech(text)` → 调用 REST；
  - `submitUndercoverVote(targetId)`；
  - `acknowledgeUndercoverResult()` → 用于阶段结束后禁用按钮。
- WebSocket handler 监听 `game.undercover.*` 事件并更新 state，同时记录到本地 `messageLog`。

### 7.3 视觉与文案
- 使用 Element Plus Steps 组件展示阶段进度。
- 文案参考《UndercoverGameRule.md》，并通过 i18n 支持中英双语。
- 对 AI 发言增加提示 tooltip，如“AI 玩家根据当前信息的推断发言”。

---

## 8. 时序与消息交互

```text
房主点击开始
  -> POST /rooms/{id}/start/
  -> RoomService 初始化 UndercoverGameSession
  -> broadcast game.undercover.stage (ROLE_DISTRIBUTION)
玩家确认
  -> DISCUSSION 阶段
    -> 玩家/AI 提交发言 (REST)
    -> broadcast game.undercover.speech
  -> VOTE_MAIN 阶段
    -> 玩家提交投票
    -> 倒计时归零后统计、广播结果
  -> 根据结果进入下一阶段 ...
  -> 游戏结束 broadcast game.undercover.result + system.broadcast 胜利方
```

重要事件顺序：
1. **阶段切换**始终先更新持久层，再广播。
2. **投票结果**需在广播前附带“票型摘要”和“被淘汰身份”；对仍未公开的身份字段进行脱敏（仅明示存活状态）。
3. **AI 执行**通过 `@Async` 任务或消息队列延迟 2-4 秒，避免与真人玩家动作冲突。

---

## 9. 边界场景处理
- **人数不足**：在 `LOBBY_READY` 阶段若当前玩家无法满足 `role_config`，禁用开始按钮并通过 `system.broadcast` 提示。
- **断线重连**：重连后立即推送 `system.sync`，其中包含 `undercoverSession.snapshot` 以及玩家当前任务状态（是否已发言/投票）。
- **超时缺投**：缺投玩家记录在 `vote_history[x].ballots` 中，标记 `auto_assigned=true`。
- **AI 接管真人**：房主可在大厅将离线玩家替换为 AI；系统生成同席位 AI 并延续回合。
- **多局连玩**：`GAME_END` 后保留词语与历史，供战报面板查看；房主点击“重开”时刷新词语并回到 `ROLE_DISTRIBUTION`。

---

## 10. 测试与验证
- **单元测试**：`UndercoverGameEngineTest` 覆盖身份分配、阶段推进、平票裁决、胜负判定。
- **集成测试**：`UndercoverGameFlowIT` 使用 MockMvc + WebSocket Stomp 客户端模拟完整回合，验证广播顺序与 payload。
- **负载测试**：模拟 100 个房间同时进行，在 `RoomSocketCoordinator` 监控广播延迟。
- **AI 行为回归**：构建固定场景脚本，比较 AI 发言/投票与预期策略，确保概率推断结果稳定。
- **前端手动验证**：通过 `docker-compose up --build`，检查倒计时同步、PK 流程、断线恢复提示。

---

## 11. 运维与监控
- 在 `UndercoverGameEngine` 中加入 `log.debug`/`log.info` 标记阶段转移、AI 行为选择。
- 利用 Prometheus 统计指标：
  - `undercover_active_rooms`、`undercover_round_duration_seconds`、
  - `undercover_ai_fallback_total`（AI 进入保守策略次数）。
- 将异常（如配置不合法、队列阻塞）上报至 `system.alert`，前端在房间顶部显示紧急提示。

---

## 12. 文档与后续
- 本文档与《UndercoverGameRule.md》共同维护；若规则或流程变更，需同步更新两者。
- 若未来引入语音输入、更多身份或自定义词库，应：
  1. 扩展 `role_config` 字段；
  2. 更新状态机对新增阶段的处理；
  3. 扩充 AI 推理算法的输入与模板。

---

通过上述设计，可以在 AISocialGame 项目中实现符合规则、体验完整、AI 智能参与的“谁是卧底”游戏模式。
