# AI Social Game Platform

基于 **Spring Boot + React (Vite)** 的社交推理游戏平台，提供真实的“谁是卧底”和“狼人杀”对局流程，支持 AI 补位、社区动态与排行榜。

## 技术栈
- 后端：Java 21、Spring Boot、JPA（MySQL）、Redis TokenStore、Maven
- 前端：React 18、Vite、Tailwind、React Query、Axios、Playwright（E2E）
- 部署：Nginx 反代 `/api`，`docker-compose` 仅编排前后端；MySQL/Redis 使用第三方服务

## 核心功能
- **房间/座位管理**：创建/加入房间、游客自动入座、AI 补位；`selfPlayerId` 便于游客断线重连。
- **实时对局通信**：新增 WebSocket/STOMP 通道（`/ws`），房间状态、座位变更、私有身份消息、聊天消息改为服务端推送。
- **v2 社交留存能力**：快速匹配、好友侧边栏、成就中心、回放中心、观战页、新手引导与规则百科已接入前端路由。
- **真实玩法**：
  - *谁是卧底*：后端分配词语与身份，座位轮流发言，收集投票并结算胜负（AI 自动发言/投票）。
  - *狼人杀*：分配角色，夜晚行动（刀人/查验/解毒/下毒）、白天发言与投票，支持超时自动推进、断线托管与自动弃票，结算刷新排行榜并发放金币。
- **社区/排行榜**：社区发帖、点赞；排行榜来源于真实对局统计。
- **身份与资产**：登录后返回真实金币/等级，结算为人类玩家追加奖励。
- **统一 SSO 登录**：前端直接访问 `/api/auth/sso/login|register?state=...`，由后端 302 跳转 user-service；回调页 `/sso/callback` 对 `state` 做严格校验后再换取本地会话。
- **房间聊天**：支持文本、快捷短语、表情消息；服务端含频率限制和夜晚文本管控。

## 快速启动
1. **准备环境**：Docker（含 compose）、Node 18+、Java 21（如需本地运行后端）。
2. **一键构建（测试环境，Docker 编排前后端）**：在仓库根目录执行：
   ```bash
   ./build.sh
   ```
   Windows PowerShell 等价命令：
   ```powershell
   .\build.ps1
   ```
   脚本会编译前后端、运行后端测试并启动 `docker-compose`（仅前后端容器）。执行前需先设置外部 MySQL/Redis 连接变量：
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
   - `SPRING_DATA_REDIS_HOST`
   - `SPRING_DATA_REDIS_PORT`
3. **生产环境脚本**：
   - Linux/macOS：`./build_prod.sh`
   - Windows PowerShell：`.\build_prod.ps1`
4. **本地直启（非 Docker，编译后直接启动）**：
   - Linux/macOS：`./build_local.sh`
   - Windows PowerShell：`.\build_local.ps1`
   - 脚本会自动加载仓库根目录环境文件（优先 `.env`，其次 `env.txt`），并将其中变量注入本次启动环境。
5. **访问**：前端入口 `http://localhost:11030` 或 `http://aisocialgame.seekerhut.com`，后端 API `http://localhost:11031/api`。

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
- 健康检查：`curl http://localhost:11031/actuator/health`。
- 排行/社区数据持久化在 MySQL 中，游客身份基于 `selfPlayerId` + 本地缓存。
- 端口约定：前端 `11030`，后端 `11031`，前端 Nginx 同时代理 `/api` 与 `/ws` 到后端服务。
- Windows 端口排除场景：若本机出现 `WinError 10013`，先检查 `netsh interface ipv4/ipv6 show excludedportrange protocol=tcp`，确认 `11031` 不在排除区间后再启动后端。

## 联调依赖（外部服务）
- MySQL：必须通过 `SPRING_DATASOURCE_*` 环境变量提供第三方实例连接信息。
- Redis：必须通过 `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` 提供第三方实例连接信息。
- Token 存储 key 前缀：`aisocialgame:auth:token:`。
- SSO 回调地址：`SSO_CALLBACK_URL`（默认 `http://aisocialgame.seekerhut.com/sso/callback`）。

更多接口说明见 `doc/api/`，模块设计见 `doc/modules/`，运行/测试流程见 `doc/test/`。
