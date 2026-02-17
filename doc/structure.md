# 项目目录结构（2026-02-16）

仅记录开发者维护的源码、配置、脚本与文档。

- `backend/`
  - `pom.xml`：后端依赖与构建配置（Spring Boot、gRPC、protobuf 代码生成、测试插件）。
  - `Dockerfile`：后端镜像构建说明。
  - `src/main/proto/`
    - `user/v1/user_service.proto`：用户服务协议。
    - `billing/v1/billing_service.proto`：积分服务协议。
    - `ai/v1/ai_service.proto`：AI 网关协议。
  - `src/main/resources/`
    - `application.yml`：应用配置（数据库、Redis、gRPC 客户端地址、项目 key、管理员配置）。
    - `prompt.yml`：AI 提示词配置。
    - `META-INF/services/io.grpc.NameResolverProvider`：注册 Consul NameResolver SPI。
  - `src/main/java/com/aisocialgame/`
    - `AiSocialGameApplication.java`：启动入口。
    - `config/`
      - `AppProperties.java`：`app.*` 配置映射（project/ai/admin）。
      - `PromptProperties.java`：提示词配置映射。
      - `TokenStoreConfig.java`：登录 token 存储实现装配。
      - `WebConfig.java`：CORS 配置。
    - `integration/consul/ConsulNameResolverProvider.java`：`consul:///` 服务发现解析。
    - `integration/grpc/client/`
      - `UserGrpcClient.java`：user-service 调用封装。
      - `BillingGrpcClient.java`：pay-service 调用封装。
      - `AiGrpcClient.java`：ai-service 调用封装。
    - `integration/grpc/dto/`：外部 gRPC 结果映射对象（用户、余额、模型、流水等）。
    - `controller/`
      - `AuthController.java`：注册/登录/me。
      - `AiController.java`：AI 模型与聊天接口。
      - `GameController.java`、`RoomController.java`、`GamePlayController.java`、`CommunityController.java`、`PersonaController.java`、`RankingController.java`：游戏大厅与玩法接口。
      - `admin/`
        - `AdminAuthController.java`：管理端登录与 token 校验。
        - `AdminDashboardController.java`：管理端概览。
        - `AdminIntegrationController.java`：外部服务联通状态。
        - `AdminUserController.java`：用户查询与封禁管理。
        - `AdminBillingController.java`：余额与流水查询。
        - `AdminAiController.java`：模型与测试对话。
    - `dto/`：业务请求响应对象（含 `Ai*`、`Auth*`、`admin/*`）。
    - `service/`
      - `AuthService.java`：用户注册登录、远端会话校验、本地 token 映射。
      - `BalanceService.java`：积分聚合。
      - `AiProxyService.java`、`AiNameService.java`、`AiGameSpeechService.java`：AI 调用与回退策略。
      - `AdminAuthService.java`、`AdminOpsService.java`：管理后台核心逻辑。
      - 其余 `GameService/RoomService/GamePlayService/CommunityService/StatsService`：游戏主流程。
      - `token/`：`TokenStore`、`RedisTokenStore`、`InMemoryTokenStore`。
    - `model/`：实体模型（含 `User` 扩展外部用户字段）。
    - `repository/`：JPA 仓库。
    - `exception/`：统一异常处理。
  - `src/test/java/com/aisocialgame/`
    - `AuthServiceTest.java`、`BalanceServiceTest.java`、`AdminAuthServiceTest.java`：v1.1 新增/重构单测。
    - `GameControllerTest.java`、`RoomServiceTest.java`、`GamePlayServiceUndercoverTest.java`：原流程回归。
  - `src/test/resources/application-test.yml`：测试配置。

- `frontend/`
  - `package.json`：前端依赖与脚本。
  - `playwright.config.ts`、`tests/`：前端 E2E 测试脚本。
  - `src/`
    - `App.tsx`：主站 + 管理台路由装配。
    - `services/api.ts`：前台 API 与管理台 API 封装。
    - `hooks/useAuth.tsx`、`hooks/useAdminAuth.tsx`：双鉴权上下文。
    - `components/layout/MainLayout.tsx`、`components/layout/AdminLayout.tsx`：前台/后台布局。
    - `pages/`：主站页面。
    - `pages/admin/`：管理台页面（登录、概览、用户、积分、AI、联通）。
    - `types/index.ts`：类型定义（扩展用户余额、AI、管理台类型）。
  - `Dockerfile`、`nginx.conf`：前端容器部署配置。

- `sql/`
  - `users.sql`：用户表（新增 external_user_id/username/session_id/access_token）。
  - `rooms.sql`、`game_states.sql`、`community_posts.sql`、`player_stats.sql`：业务表。
  - `schema.sql`：全量表结构汇总（v1.1）。

- `doc/`
  - `api/`：控制器接口文档（含新增 `AiController` 与 `Admin*Controller`）。
  - `api/external/`：外部微服务 gRPC 接口沉淀文档。
  - `modules/`：模块说明（新增 gRPC 集成、管理后台模块）。
  - `test/operation.md`：系统操作步骤。
  - `test/integratedTest.md`：集成测试清单与结果。
  - `issues.md`：当前阻塞与建议。

- `design-doc/`
  - `v1.1/00-前置检查与服务摸底.md`：grpcurl、Consul、反射探测记录。
  - `v1.1/01-版本规划.md`：v1.1 目标与里程碑。
  - `v1.1/02-技术设计.md`：架构与实施方案。

- `docker-compose.yml`：前后端 + MySQL + Redis 编排（含 gRPC/Consul 相关环境变量）。
- `build.sh`：构建、测试、重启 compose 的部署脚本。
