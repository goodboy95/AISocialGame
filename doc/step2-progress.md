# 步骤二交付总结：房间系统与实时通信

本文记录步骤二的关键成果，帮助新成员快速了解当前实现与后续可扩展点。

## 1. 后端房间能力

- `Room` / `RoomPlayer` 模型完善：新增状态、阶段、人数上限、配置、更新时间字段，以及座位、房主标记、AI 占位等信息。
- 服务层封装创建/加入/退出/解散/开始流程，包含人数校验、房主转移、系统广播，并通过 Channels 群组推送。
- `RoomViewSet` 暴露 REST 接口（创建、列表、详情、加入、按房号加入、退出、开始、解散），与 JWT 鉴权结合。
- `RoomConsumer` 支持 JWT 握手、房间成员校验、系统同步、聊天广播；ASGI Routing 绑定 `ws/rooms/<room_id>/`。
- 新增 `config.settings.test`、InMemory Channel Layer、`pytest` 测试用例覆盖 REST 与 WebSocket 场景。

## 2. 前端大厅与房间体验

- `useRoomsStore` 整合房间列表、详情、加入/退出、房号加入、WebSocket 消息管理，兼容系统广播与聊天。
- 大厅页新增搜索、状态筛选、房号加入、创建房间弹窗，展示房主/人数/房间状态。
- 房间页展示成员席位、房主标识、连接状态、实时聊天输入框，支持房主“开始游戏”与成员“离开房间”。
- `GameSocket` 支持获取底层 WebSocket 实例，统一通过 `VITE_WS_BASE_URL` 配置连接地址。

## 3. 工具与说明

- README 更新房间 REST / WebSocket 接口及测试指引。
- 前端补充 `.env.example`，声明 `VITE_API_BASE_URL`、`VITE_WS_BASE_URL`。
- `pytest` 作为默认测试命令，确保步骤二核心逻辑可回归。

## 4. 建议的下一步

- 基于现有房间能力实现“谁是卧底”回合流程与游戏状态机。
- 扩展系统消息协议（投票、AI 提示等），完善断线重连体验。
- 引入前端端到端测试（如 Cypress）验证大厅与房间关键交互。

## 5. 新成员上手指南

1. **了解模型与服务**：阅读 `backend/apps/rooms/models.py` 与 `services.py`，熟悉房间/成员的核心字段及业务入口。
2. **熟悉接口**：通过 `backend/apps/rooms/serializers.py`、`views.py` 了解 REST 数据结构，结合 `doc/socialGameDevStep2.md` 的接口速查表进行联调。
3. **运行测试**：在后端执行 `pytest`，确保环境配置正确；新增逻辑时参考现有用例编写测试。
4. **前端调试**：查看 `frontend/src/store/rooms.ts` 和 `pages/room` 组件，理解状态流转；修改消息协议时务必同步更新 store 的 `handleSystemMessage`。
5. **文档维护**：新增功能后记得更新根目录及前后端 README，并在 `doc/` 中补充阶段总结，保持交接资料同步。
