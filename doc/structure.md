# 项目目录结构（2025-11-27）

仅列出开发者编写/需要关注的文件与目录。

- `backend/`
  - `pom.xml`：Spring Boot + Maven 项目配置（JDK 25，编译 release 21；集成 JPA、MySQL 驱动、Redis、Actuator）。
  - `Dockerfile`：后端生产镜像（当前 compose 直接用官方 JRE 镜像挂载已编译 JAR，无需本地 build）。
  - `src/main/java/com/aisocialgame/`
    - `AiSocialGameApplication.java`：Spring Boot 入口。
    - `config/WebConfig.java`：全局 CORS 配置；`config/TokenStoreConfig.java`：按 profile 装配 Redis/InMemory token store；`config/PromptProperties.java`：集中加载 `prompt.yml` 中的 AI 提示词与昵称词库。
    - `controller/`：`AuthController`、`GameController`、`RoomController`、`PersonaController`、`GamePlayController`（游戏内逻辑）、`CommunityController`（社区发帖）、`RankingController`（排行榜）。
    - `dto/`：请求/响应 DTO（注册、登录、创建房间、加入房间、AI 添加、发言/投票/夜晚行动、社区发帖等）。
    - `exception/`：统一异常与校验处理。
    - `model/`：领域模型（Game、Room、RoomSeat、Persona、User、GameState/GamePlayerState/GameLogEntry、CommunityPost、PlayerStats、UndercoverWordPair 等）；`model/converter/` 负责 JSON 转换（房间配置、游戏状态、字符串列表等）。
    - `repository/`：Spring Data JPA 仓库（`UserRepository`、`RoomRepository`、`GameStateRepository`、`CommunityPostRepository`、`PlayerStatsRepository`）与内置种子仓库（游戏/人设/卧底词库）。
    - `service/`：业务逻辑（鉴权、游戏列表含动态在线人数、房间管理、AI 人设、`AiNameService` 为新 AI 随机生成昵称且可调用外部 AI 接口、`GamePlayService` 驱动卧底/狼人杀真实流程并在 `state` 轮询时推进 AI 发言/投票、`CommunityService` 发帖、`StatsService` 结算并更新排行榜）；`service/token/` 包含 Redis 与测试用内存 token store 实现。
  - `src/test/java/com/aisocialgame/`：单元/接口测试（GameControllerTest、RoomServiceTest、AuthServiceTest）。
  - `src/test/java/com/aisocialgame/GamePlayServiceUndercoverTest.java`：卧底流程单测，覆盖 AI 逐人发言与全员投票后才结算的约束。
  - `src/test/resources/application-test.yml`：测试 profile，使用 H2 内存库并禁用 Redis 自动配置。
  - `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`：强制 Mockito 使用子类 mock maker，避免容器内 JVMTI 限制导致的测试失败。
  - `src/main/resources/application.yml`：应用配置（端口 8080、MySQL/Redis 连接、JPA ddl-auto、token TTL），通过 `spring.config.import` 引入 `prompt.yml`。
  - `src/main/resources/prompt.yml`：AI 名称词库与提示词模板（描述、怀疑、投票日志等），业务代码全部从该配置读取。
  - `target/ai-social-game-0.0.1-SNAPSHOT.jar`：打包产物（由 Docker 构建阶段生成）。

- `frontend/`
  - `package.json` / `pnpm-lock.yaml`：前端依赖（React 18 + Vite + Tailwind + React Query + Playwright）。
  - `playwright.config.ts`、`tests/basic.spec.ts`、`tests/full-flow.spec.ts`：端到端校验（首页跳转、注册登录-开房-发帖全流程）。
  - `.dockerignore`：避免将 `node_modules` 等开发产物打进镜像，防止符号链接指向宿主路径。
  - `src/`
    - `App.tsx`：全局路由 & Provider 挂载。
    - `hooks/useAuth.tsx`：登录态/游客名管理，封装登录注册并输出头像。
    - `services/api.ts`：Axios 实例与后端 API 封装（默认 `baseURL=/api`，新增 gameplay/community/ranking API）。
    - `types/`：前端公共类型定义（Game、Room、Persona、User、GameState、CommunityPost、PlayerStats 等）。
    - `pages/`：业务页面（Index/RoomList/CreateRoom/Lobby/Login/Register/Profile/Community/Rankings，以及 `games/` 下的狼人杀、卧底真实玩法页）。页面数据通过 React Query 实时拉取后端，房间列表卡片会根据房间属性渲染“私密/语音/板子/人数”等标签，玩法页通过新状态接口驱动发言/投票/夜晚行动。
    - `components/layout/MainLayout.tsx`：导航、用户信息展示、登出入口（金币显示改为真实数据）。
  - `Dockerfile`：前端构建并由 Nginx 托管静态站点，反向代理 `/api` 到后端。
  - `public/`：静态资源。

- `infra/docker/dev/*`：开发容器 Dockerfile（保留，未用于生产）。

- `docker-compose.yml`：编排前端、后端、MySQL、Redis（MySQL 暴露 3307，Redis 暴露 6380，数据默认持久化到 `/var/lib/aisocialgame/*`），带健康检查；后端使用 `eclipse-temurin:21-jre` 运行挂载的 JAR，前端使用 `nginx:1.27-alpine` 挂载 `dist` 与 `nginx.conf`。
- `build.sh`：CI/本地一键构建、测试、准备数据目录、重启 docker-compose 的脚本（Docker 容器内 Maven 编译、pnpm 构建前端，随后仅 `docker compose pull && up`，不再本地构建镜像；Playwright e2e 可按需手工执行）。
- `sql/`：数据库建表脚本，每个表一个文件（`users.sql`、`rooms.sql`、`player_stats.sql`、`community_posts.sql`、`game_states.sql`），便于初始化/迁移。

- `doc/`
  - 设计稿：`平台设计.md`、`狼人杀房间设计.md`、`谁是卧底房间设计.md`。
  - `api/`：各 Controller 接口文档。
  - `modules/`：模块说明（示例：大厅/房间模块）。
  - `test/integratedTest.md`：集成测试清单与执行结果。
  - `test/operation.md`：系统功能清单与逐步操作指引。
  - `issues.md`：未解决问题记录（若空则表示无阻塞项）。
