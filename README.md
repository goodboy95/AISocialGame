# AI Social Game Platform

本项目旨在构建一个支持实时互动、AI 助手的在线社交游戏平台。本仓库采用前后端分离的结构，后端基于 Django + DRF + Channels，前端基于 Vue 3 + Vite + Element Plus，并提供 Docker Compose 一键启动能力。

## 功能进展

- **房间管理 REST API**：支持创建、查询、分页、按房号加入/退出，并提供房主解散/踢人、开始游戏等操作，内置权限校验与并发控制；
- **实时通信基建**：基于 Django Channels + Redis + Daphne，按房间维度维护 WebSocket 分组，提供文本聊天、系统广播与游戏事件推送协议；
- **“谁是卧底”引擎**：落地通用 `BaseGameEngine` 与 `UndercoverEngine` 状态机，完成词库抽取、角色发牌、发言/投票流程、胜负判定以及 AI 玩家自动补位与行动；
- **“狼人杀”玩法**：新增 `WerewolfEngine`，实现夜晚行动、白天发言与投票的多阶段状态机；支持角色配置、女巫解救/毒药、预言家查验、私密信息透出，并扩展 `RoomPlayer` 字段以及 AI 行动策略库；
- **多玩法调度**：`ENGINE_REGISTRY` 支持按房间配置选择引擎，REST/WebSocket 底层兼容不同状态机，前端房间面板可在“谁是卧底”与“狼人杀”之间无缝切换；
- **前端大厅与房间体验**：大厅页支持搜索、筛选、房号加入与创建房间；房间页升级为游戏面板，展示身份词语、阶段提示、发言记录、投票按钮及实时聊天；
- **自动化测试**：使用 `pytest`、`pytest-django` 与 Channels 测试工具覆盖房间 REST/WS 流程、“谁是卧底”与“狼人杀”核心回合，作为回归基线；

## 仓库结构

```text
backend/   # Django + Channels 后端服务
frontend/  # Vue 3 + Vite 前端工程
doc/       # 设计文档与阶段总结
```

## 快速开始

### 准备环境

1. 安装 [Docker Desktop](https://www.docker.com/) 或兼容的 Docker Engine。
2. 克隆仓库后，复制环境变量模板：

   ```bash
   cp backend/.env.example backend/.env
   cp frontend/.env.example frontend/.env
   ```

   根据实际情况修改数据库、AI 接口等配置。

### 本地启动（Docker Compose）

```bash
docker-compose up --build
```

- 前端默认运行在 `http://localhost:5173`
- 后端 REST API 位于 `http://localhost:8000/api`
- 健康检查接口：`http://localhost:8000/api/health/`

首次启动会初始化 MySQL 与 Redis 容器，并安装前后端依赖。

### 直接运行后端（可选）

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements/base.txt
cp .env.example .env
python manage.py migrate
python manage.py runserver
```

### 直接运行前端（可选）

```bash
cd frontend
npm install
npm run dev
```

> 提示：若未在 `.env` 中显式设置 `VITE_WS_BASE_URL`，前端会优先基于 `VITE_API_BASE_URL` 自动推导 WebSocket 基础地址（例如 `http://localhost:8000/api` 会映射到 `ws://localhost:8000/ws`），若未配置 API 地址，则回退到当前访问域名的 `/ws`。

## 功能验证指南

1. **创建账号并获取令牌**（示例命令基于 [HTTPie](https://httpie.io/)）：

   ```bash
   # 注册用户
   http POST http://localhost:8000/api/auth/register/ username=alice password=Passw0rd! display_name=Alice

   # 登录获取 JWT
   http POST http://localhost:8000/api/auth/token/ username=alice password=Passw0rd!
   ```

   登录成功后将返回 `access`、`refresh` 令牌，后续请求在 Header 中携带 `Authorization: Bearer <access>`。

2. **创建并加入房间**：

   ```bash
   http POST http://localhost:8000/api/rooms/ name="Alice 的房间" max_players:=6 Authorization:"Bearer <access>"
   http POST http://localhost:8000/api/rooms/{id}/join/ Authorization:"Bearer <access>"
   ```

   创建成功会返回房间 ID 与房号 (`code`)，可通过 `POST /api/rooms/join-by-code/` 邀请其他成员加入。

3. **验证实时聊天与游戏事件**：使用浏览器或任意 WebSocket 客户端连接 `ws://localhost:8000/ws/rooms/<id>/?token=<access>`，连接成功后会收到房间快照（`system.sync`）。
   - 聊天消息示例：

     ```json
     {
       "type": "chat.message",
       "payload": {"content": "Hello Room"}
     }
     ```

   - 游戏事件示例：房主点击“开始游戏”后，可发送 `ready` 事件进入发言阶段：

     ```json
     {
       "type": "game.event",
       "payload": {"event": "ready"}
     }
     ```

     发言玩家可使用 `submit_speech`，投票阶段可使用 `submit_vote` 事件，所有房间成员会收到 `game.event` 广播并刷新前端面板。

4. **房间操作与“游戏发言区域”**：
   - 页面右上角的状态标签会显示 `在线/离线`，绿色“在线”表示 WebSocket 已连通，可进行聊天与游戏操作。
   - 房主点击“开始游戏”后，中央的阶段面板会展示当前阶段与倒计时；当显示“通知开始发言”按钮时，需要房主点击一次以进入发言轮。
   - 在发言阶段，阶段面板右侧即是“游戏发言区域”：左侧 `Speech Timeline` 会实时记录每位玩家的发言；当前发言玩家可在右侧文本框输入内容并点击“提交发言”。其他玩家会看到“等待当前玩家发言”的提示。
   - 非发言阶段时，`Speech Timeline` 仍会保留历史记录；该区域不包含公屏或私聊消息，专用于轮流发言的记录与回放。
   - 页面右侧的聊天区域提供“公屏”“私聊”“阵营”三类频道，可在输入框输入内容后点击“发送”或按回车发送消息，历史会自动滚动到底部。
   - 当处于投票阶段时，阶段面板会列出存活玩家按钮；点击即提交投票，进度条会实时更新，按钮灰显表示已投票。
   - 房主可以在“成员列表”中通过每个玩家行尾的“移出”按钮踢出成员（仅在房间状态为“等待”时可用）；若需要直接结束对局，可使用页头的“解散房间”按钮解散整个房间。

5. **运行自动化测试**：

   ```bash
   cd backend
   pytest
   ```

   测试覆盖房间 REST/WS 核心流程，可作为回归基线。

## 下一阶段规划

- 引入倒计时与默认操作机制，保障多阶段玩法的超时处理体验；
- 丰富战局复盘、战绩记录与可视化分析，沉淀对局数据资产；
- 对接真实大模型接口，支持多风格 AI 发言/决策与私聊频道消息管理；

更多细节请参考 `doc/` 目录中的阶段性文档。
