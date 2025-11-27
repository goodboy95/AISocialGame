# AI Social Game Platform

基于 **Spring Boot + React (Vite)** 的社交推理游戏平台，提供真实的“谁是卧底”和“狼人杀”对局流程，支持 AI 补位、社区动态与排行榜。

## 技术栈
- 后端：Java 21、Spring Boot、JPA（MySQL）、Redis TokenStore、Maven
- 前端：React 18、Vite、Tailwind、React Query、Axios、Playwright（E2E）
- 部署：Nginx 反代 `/api`，`docker-compose` 编排前后端 + MySQL + Redis

## 核心功能
- **房间/座位管理**：创建/加入房间、游客自动入座、AI 补位；`selfPlayerId` 便于游客断线重连。
- **真实玩法**：
  - *谁是卧底*：后端分配词语与身份，座位轮流发言，收集投票并结算胜负（AI 自动发言/投票）。
  - *狼人杀*：分配角色，夜晚行动（刀人/查验/解毒/下毒）、白天发言与投票，支持超时自动推进，结算刷新排行榜并发放金币。
- **社区/排行榜**：社区发帖、点赞；排行榜来源于真实对局统计。
- **身份与资产**：登录后返回真实金币/等级，结算为人类玩家追加奖励。

## 快速启动
1. **准备环境**：Docker（含 compose）、Node 18+、Java 21（如需本地运行后端）。
2. **一键构建**：在仓库根目录执行：
   ```bash
   ./build.sh
   ```
   脚本会编译前后端、运行后端测试（若环境有 Maven）、构建镜像并启动 `docker-compose`。
3. **访问**：前端入口 `http://localhost`，后端 API `http://localhost:8080/api`。
4. **手动运行（可选）**：
   - 后端：`cd backend && mvn spring-boot:run`
   - 前端：`cd frontend && pnpm install && pnpm dev`

## LLM 接入配置（OpenAI 兼容）
平台预留了统一的 LLM 配置，遵循 OpenAI API 格式：
- **后端环境变量**：
  - `APP_LLM_BASE_URL`：OpenAI 兼容地址（如 `https://api.openai.com/v1`）。
  - `APP_LLM_API_KEY`：密钥。
  - `APP_LLM_MODEL`：模型名称（如 `gpt-4o-mini`）。
- **前端环境变量**（可选，用于直连模型的前端功能时）：
  - `VITE_LLM_BASE_URL`
  - `VITE_LLM_API_KEY`
  - `VITE_LLM_MODEL`

配置后端时在启动命令/Compose 环境中导出上述变量即可；前端对应写入 `frontend/.env`。接口调用采用 `Authorization: Bearer <key>`，请求体与 OpenAI Chat/Completions 兼容。

## 测试与运维
- 后端单测：`cd backend && mvn test`（当前环境若缺少 Maven 可跳过，容器构建会自动执行）。
- 前端构建：`cd frontend && pnpm build`；E2E：`pnpm test:e2e`（需已启动后端）。
- 健康检查：`curl http://localhost:8080/actuator/health`。
- 排行/社区数据持久化在 MySQL 中，游客身份基于 `selfPlayerId` + 本地缓存。

更多接口说明见 `doc/api/`，模块设计见 `doc/modules/`，运行/测试流程见 `doc/test/`。
