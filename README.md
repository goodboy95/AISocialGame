# AI Social Game Platform

本项目旨在构建一个支持实时互动、AI 助手的在线社交游戏平台。本仓库采用前后端分离的结构，后端基于 Django + DRF + Channels，前端基于 Vue 3 + Vite + Element Plus，并提供 Docker Compose 一键启动能力。

## 功能进展

- **房间管理 REST API**：支持创建、查询、分页、按房号加入/退出、房主解散与开始游戏等操作，并进行了权限校验与并发控制。
- **实时通信基建**：基于 Django Channels + Redis + Daphne，按房间维度维护 WebSocket 分组，提供文本聊天、系统广播与游戏事件推送协议。
- **“谁是卧底”引擎**：落地通用 `BaseGameEngine` 与 `UndercoverEngine` 状态机，完成词库抽取、角色发牌、发言/投票流程、胜负判定以及 AI 玩家自动补位与行动。
- **前端大厅与房间体验**：大厅页支持搜索、筛选、房号加入与创建房间；房间页升级为游戏面板，展示身份词语、阶段提示、发言记录、投票按钮及实时聊天。
- **自动化测试**：使用 `pytest`、`pytest-django` 与 Channels 测试工具覆盖房间 REST/WS 流程及“谁是卧底”引擎核心回合，作为回归基线。

## 仓库结构

```text
backend/   # Django + Channels 后端服务
frontend/  # Vue 3 + Vite 前端工程
 doc/      # 设计文档与阶段总结
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

## 功能验证指南

1. **创建账号并获取令牌**（示例命令基于 [HTTPie](https://httpie.io/)）
   ```bash
   # 注册用户
   http POST http://localhost:8000/api/auth/register/ username=alice password=Passw0rd! display_name=Alice

   # 登录获取 JWT
   http POST http://localhost:8000/api/auth/token/ username=alice password=Passw0rd!
   ```
   登录成功后将返回 `access`、`refresh` 令牌，后续请求在 Header 中携带 `Authorization: Bearer <access>`。

2. **创建并加入房间**
   ```bash
   http POST http://localhost:8000/api/rooms/ name="Alice 的房间" max_players:=6 Authorization:"Bearer <access>"
   http POST http://localhost:8000/api/rooms/{id}/join/ Authorization:"Bearer <access>"
   ```
   创建成功会返回房间 ID 与房号 (`code`)，可通过 `POST /api/rooms/join-by-code/` 邀请其他成员加入。

3. **验证实时聊天与游戏事件**
   使用浏览器或任意 WebSocket 客户端，连接 `ws://localhost:8000/ws/rooms/<id>/?token=<access>`，连接成功后会收到房间快照（`system.sync`）。
   - 聊天消息示例：
     ```json
     {
       "type": "chat.message",
       "payload": {"content": "Hello Room"}
     }
     ```
   - 游戏事件示例：房主在 REST 接口点击开始后，可发送 `ready` 事件进入发言阶段：
     ```json
     {
       "type": "game.event",
       "payload": {"event": "ready"}
     }
     ```
     发言玩家可使用 `submit_speech`，投票阶段可使用 `submit_vote` 事件，所有房间成员会收到 `game.event` 广播并刷新前端面板。

4. **运行自动化测试**
   ```bash
   cd backend
   pytest
   ```
   测试覆盖房间 REST/WS 核心流程，可作为回归基线。

## 下一阶段规划

- 扩展更多玩法（如狼人杀）并提炼通用配置，支持多引擎并行运行。
- 引入游戏内倒计时、结果复盘、回合复盘存档等高级功能。
- 优化 AI 语料与策略，可配置不同性格、重试与降级方案。

更多细节请参考 `doc/` 目录中的阶段性文档。
