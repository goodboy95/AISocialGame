# AI Social Game Platform

本项目旨在构建一个支持实时互动、AI 助手的在线社交游戏平台。本仓库采用前后端分离的结构，后端基于 Java 21 + Spring Boot，前端基于 Vue 3 + Vite + Element Plus，并提供 Docker Compose 一键启动能力。

## 功能进展

- **房间管理 REST API**：Spring Boot 提供的接口支持创建、查询、分页、按房号加入/退出，并提供房主解散/踢人、开始游戏等操作，内置权限校验；
- **简化的游戏回合模拟**：房主点击“开始游戏”后后端会自动分配身份词条、生成投票统计占位等基础状态，以驱动前端的“谁是卧底”界面；
- **AI 玩家元数据**：提供 `/api/meta/styles/` 接口返回预置的 AI 风格描述，前端添加 AI 玩家时可选择对应策略；
- **词库维护**：提供词条的增删改查、批量导入与导出接口，方便扩展“谁是卧底”题库（入口已迁移至管理后台）；
- **前端大厅与房间体验**：大厅页支持搜索、筛选、房号加入与创建房间；房间页延续既有 UI 展示身份信息、发言记录与玩家列表；
- **管理员权限与管理后台**：用户信息新增 `is_admin` 字段，后端开放 `/manage/**` 管理接口（仅管理员可访问），根目录新增 `manage/` 前端负责 AI 模型与游戏配置维护；
- **AI 提示词模板管理**：新增 `AiPromptService` 统一处理 AI 玩家提示词，管理后台提供“提示词管理”页，可按游戏、角色、阶段维护数据库中的系统提示词模板，并自动注入“性格与倾向”等上下文；
- **自动化构建**：使用 Maven Wrapper 统一依赖与构建流程，可在 CI/CD 或本地直接执行 `./mvnw package`。

## 仓库结构

```text
backend/   # Spring Boot 后端服务
frontend/  # Vue 3 + Vite 前端工程
manage/    # 管理后台前端工程（共用登录态）
doc/       # 设计文档与阶段总结
```

## 快速开始

### 准备环境

1. 安装 [Docker Desktop](https://www.docker.com/) 或兼容的 Docker Engine。
2. 克隆仓库后，复制环境变量模板：

   ```bash
   cp frontend/.env.example frontend/.env
   ```

   根据实际情况修改前端请求的 API 地址等配置。后端默认使用内存数据库，可通过环境变量覆盖。

### 本地启动（Docker Compose）

```bash
docker-compose up --build
```

- 前端默认运行在 `http://localhost:5100`
- 后端 REST API 位于 `http://socialgame.seekerhut.com/api`

首次启动会构建 Spring Boot 可执行包并安装前端依赖。

### 直接运行后端（可选）

```bash
cd backend
./mvnw spring-boot:run
```

### 直接运行前端（可选）

```bash
cd frontend
npm install
npm run dev
```

> 提示：若未在 `.env` 中显式设置 `VITE_WS_BASE_URL`，前端会优先基于 `VITE_API_BASE_URL` 自动推导 WebSocket 基础地址（例如 `http://socialgame.seekerhut.com/api` 会映射到 `ws://socialgame.seekerhut.com/ws`），若未配置 API 地址，则回退到当前访问域名的 `/ws`。

### 直接运行管理前端（可选）

```bash
cd manage
npm install
npm run dev
```

管理后台默认读取主站浏览器中的 JWT 凭证，无需重新登录。可通过设置 `VITE_MANAGE_BASE_URL` 指定部署地址（默认为 `/manage`）。

## 功能验证指南

1. **创建账号并获取令牌**（示例命令基于 [HTTPie](https://httpie.io/)）：

   ```bash
   # 注册用户
   http POST http://socialgame.seekerhut.com/api/auth/register/ username=alice password=Passw0rd! display_name=Alice

   # 登录获取 JWT
   http POST http://socialgame.seekerhut.com/api/auth/token/ username=alice password=Passw0rd!
   ```

   登录成功后将返回 `access`、`refresh` 令牌，后续请求在 Header 中携带 `Authorization: Bearer <access>`。

2. **创建并加入房间**：

   ```bash
   http POST http://socialgame.seekerhut.com/api/rooms/ name="Alice 的房间" max_players:=6 Authorization:"Bearer <access>"
   http POST http://socialgame.seekerhut.com/api/rooms/{id}/join/ Authorization:"Bearer <access>"
   ```

   创建成功会返回房间 ID 与房号 (`code`)，可通过 `POST /api/rooms/join-by-code/` 邀请其他成员加入。

3. **房间操作与“游戏发言区域”**：
   - 页面右上角的状态标签会显示 `在线/离线`，绿色“在线”表示 WebSocket 已连通，可进行聊天与游戏操作。
   - 房主点击“开始游戏”后，中央的阶段面板会展示当前阶段与倒计时；当显示“通知开始发言”按钮时，需要房主点击一次以进入发言轮。
   - 在发言阶段，阶段面板右侧即是“游戏发言区域”：左侧 `Speech Timeline` 会实时记录每位玩家的发言；当前发言玩家可在右侧文本框输入内容并点击“提交发言”。其他玩家会看到“等待当前玩家发言”的提示。
   - 非发言阶段时，`Speech Timeline` 仍会保留历史记录；该区域不包含公屏或私聊消息，专用于轮流发言的记录与回放。
   - 页面右侧的聊天区域提供“公屏”“私聊”“阵营”三类频道，可在输入框输入内容后点击“发送”或按回车发送消息，历史会自动滚动到底部。
   - 当处于投票阶段时，阶段面板会列出存活玩家按钮；点击即提交投票，进度条会实时更新，按钮灰显表示已投票。
   - 房主可以在“成员列表”中通过每个玩家行尾的“移出”按钮踢出成员（仅在房间状态为“等待”时可用）；若需要直接结束对局，可使用页头的“解散房间”按钮解散整个房间。

4. **运行后端构建**：

   ```bash
   cd backend
   ./mvnw test
   ```

   该命令会执行 Spring Boot 的单元测试并生成可运行的 Jar 包。

5. **访问管理后台**：

   - 确保目标账号的 `is_admin` 标记为 `1`（可通过数据库或后续迁移脚本设置）；
   - 管理员访问 `https://<部署域名>/manage/`（或本地运行的管理前端地址）即可进入后台；
   - 后台提供 AI 模型配置、AI 角色维护、“谁是卧底”词库管理，以及全局“提示词管理”页，可为“谁是卧底”“狼人杀”等场景配置各阶段的 AI 系统提示词；

更多细节请参考 `doc/` 目录中的架构、功能与使用指南（architecture.md、features.md、usage.md）。
