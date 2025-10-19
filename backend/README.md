# 后端服务（Spring Boot）

该目录包含使用 Java 21 与 Spring Boot 3 编写的后端服务，提供大厅/房间管理、玩家注册登录、AI 角色元数据与词库维护等 REST API，并通过 WebSocket 与前端保持实时同步。

## 技术栈

- Java 21、Spring Boot 3.2
- Spring MVC + Spring Security + Spring Data JPA
- H2（默认内存数据库，可通过环境变量切换到 MySQL/PostgreSQL）
- JSON Web Token (JWT) 认证
- Spring WebSocket + Jackson 序列化

## 目录结构

```text
src/main/java/com/aisocialgame/backend/
├─ controller/   # Auth、Room、Meta、WordPair 控制器
├─ service/      # 房间、认证、词库等领域逻辑与事件发布
├─ realtime/     # WebSocket 配置、协调器与事件监听
├─ security/     # JWT 过滤器、用户详情、密码配置
├─ entity/       # UserAccount、Room、RoomPlayer、GameSession、WordPair 等实体
├─ dto/          # REST 响应/请求 DTO
├─ repository/   # Spring Data JPA 仓储接口
└─ config/       # 安全、WebSocket、AI 风格配置
```

`src/main/resources` 中包含 `application.yml`（默认端口、H2 数据库、房号长度等）与 `logback-spring.xml` 日志配置。

## 运行

```bash
cd backend
./mvnw spring-boot:run
```

默认监听 `http://localhost:8000/api`。若需连接外部数据库，可设置：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_DRIVER`

JWT 相关参数可通过环境变量覆写：

- `JWT_SECRET`
- `JWT_ACCESS_TTL`
- `JWT_REFRESH_TTL`

额外参数：

- `AI_SOCIAL_ROOM_CODE_LENGTH`：房号长度，默认 6。【F:backend/src/main/resources/application.yml†L1-L44】
- `AI_SOCIAL_DEFAULT_ENGINE`：默认游戏引擎（`undercover` 或 `werewolf`）。

## WebSocket 与事件

- `/ws/rooms/{roomId}`：主入口，使用 `token` 查询参数携带 JWT。`RoomSocketHandler` 会校验房间权限并通过 `RoomSocketCoordinator` 注册连接，随后推送 `system.sync` 快照。【F:backend/src/main/java/com/aisocialgame/backend/realtime/RoomSocketHandler.java†L33-L151】
- `RoomRealtimeEvents`：服务层通过 `ApplicationEventPublisher` 发布房间事件（加入、离开、聊天、游戏阶段更新等）。监听器 `RoomRealtimeListener` 将事件转换为广播或单播消息。【F:backend/src/main/java/com/aisocialgame/backend/realtime/RoomRealtimeEvents.java†L6-L103】【F:backend/src/main/java/com/aisocialgame/backend/realtime/RoomRealtimeListener.java†L18-L126】
- 聊天频道：`chat.message`（公屏）、`chat.direct`（私聊）、`chat.faction`（阵营）。若新增频道请同时扩展前端类型与监听逻辑。

## 主要业务流程

- **房间管理**：`RoomService` 负责房间创建、加入、离开、踢人、AI 玩家插入与房间状态更新。在 `startGame` 时会初始化 `GameSession` 并分配身份词条，随后发布广播事件。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L46-L364】
- **词库维护**：`WordPairController` + `WordPairService` 支持分页、搜索、单条 CRUD、批量导入/导出。导入支持 CSV/JSON，导出会返回 UTF-8 CSV。【F:backend/src/main/java/com/aisocialgame/backend/controller/WordPairController.java†L21-L210】
- **认证与刷新**：`AuthController` 提供注册、登录、刷新、注销接口，并在 `AccountUserDetailsService` 中校验凭据；刷新令牌持久化在 `RefreshToken` 表。【F:backend/src/main/java/com/aisocialgame/backend/controller/AuthController.java†L27-L171】
- **AI 风格元数据**：`MetaController` 暴露 `/api/meta/styles/`，返回配置在 `application.yml` 的预置 AI 策略，供前端展示。【F:backend/src/main/java/com/aisocialgame/backend/controller/MetaController.java†L16-L58】

## 数据模型速览

- `UserAccount`：用户名、展示名、密码（BCrypt）、角色。
- `Room`：名称、房号、状态、引擎、配置 JSON、创建/更新时间。
- `RoomPlayer`：席位号、是否房主/AI、是否存活、词条、角色。
- `GameSession`：房间关联、当前阶段、回合数、JSON 状态与计时器。
- `WordPair`：题目关键词、描述、适用引擎标签。

## 开发与测试

- 运行全部测试：`./mvnw test`
- 代码格式：遵循 Spring 格式，保持服务层负责业务、控制层仅做参数校验与委托；
- 推荐为关键服务（房间生命周期、词库导入）编写单元或集成测试，位于 `src/test/java`（若不存在可创建）。

### 常见陷阱

- **分页**：`RoomService.listRooms` 目前在内存中过滤/排序大房间列表，若房间数量较多需迁移到数据库分页或加入缓存。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L94-L114】
- **多实例部署**：WebSocket 状态存储在应用内存的 `RoomSocketCoordinator` 中，多副本部署需引入集中式会话存储或消息总线。
- **房主权限**：部分操作（踢人、开始游戏、解散房间）依赖 `@PreAuthorize` 注解；若新增接口，请同步更新安全注解与测试。

## 接口概览

- 认证：`POST /api/auth/register/`、`/token/`、`/token/refresh/`、`/logout/`、`GET /api/auth/me/`、`/me/export/`、`DELETE /api/auth/me/`
- 房间：`GET /api/rooms/`、`POST /api/rooms/`、`GET /api/rooms/{id}/`、`POST /api/rooms/{id}/join/`、`POST /api/rooms/{id}/leave/`、`POST /api/rooms/{id}/start/`、`POST /api/rooms/{id}/add-ai/`、`POST /api/rooms/{id}/kick/`、`DELETE /api/rooms/{id}/`、`POST /api/rooms/join-by-code/`
- 元数据：`GET /api/meta/styles/`
- 词库：`GET /api/games/word-pairs/`、`POST /api/games/word-pairs/`、`PATCH /api/games/word-pairs/{id}/`、`DELETE /api/games/word-pairs/{id}/`、`POST /api/games/word-pairs/import/`、`GET /api/games/word-pairs/export/`

接口返回遵循前端所需的字段结构，部分游戏流程以轻量化模拟数据提供，扩展真实逻辑时可在 `GameSession.state` 中增加额外字段。
