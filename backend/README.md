# 后端服务（Spring Boot）

该目录包含使用 Java 21 与 Spring Boot 3 编写的后端服务，提供大厅/房间管理、玩家注册登录、AI 角色元数据与词库维护等 REST API，保持与既有前端的接口约定兼容。

## 技术栈

- Java 21
- Spring Boot 3.2
- Spring MVC + Spring Security + Spring Data JPA
- H2（默认内存数据库，可通过环境变量切换到 MySQL）
- JSON Web Token (JWT) 认证

## 主要模块

- `config/`：应用配置（安全策略、JWT、AI 风格配置属性）。
- `controller/`：REST API 控制器。
- `dto/`：请求/响应数据传输对象。
- `entity/`：JPA 实体模型，覆盖用户、房间、玩家、游戏会话、词库等。
- `repository/`：Spring Data 仓储接口。
- `security/`：JWT 生成与过滤器、用户详情服务。
- `service/`：业务服务实现，包括房间管理、认证、用户信息导出、词库 CRUD 等。

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

## 可用接口概览

- `POST /api/auth/register/`、`/token/`、`/token/refresh/`、`/logout/`
- `GET /api/auth/me/`、`/me/export/`、`DELETE /api/auth/me/`
- `GET /api/rooms/`、`POST /api/rooms/`
- 房间操作：`/join/`、`/leave/`、`/start/`、`/add-ai/`、`/kick/`、`/join-by-code/`、`DELETE /api/rooms/{id}/`
- `GET /api/meta/styles/`
- 词库：`GET /api/games/word-pairs/`、`POST /api/games/word-pairs/`、`PATCH /api/games/word-pairs/{id}/`、`DELETE /api/games/word-pairs/{id}/`、`POST /api/games/word-pairs/import/`、`GET /api/games/word-pairs/export/`

接口返回遵循前端所需的字段结构，部分业务（如游戏流程）以轻量化模拟数据提供。
