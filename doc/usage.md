# 使用与开发指南

## 前置条件

- Node.js >= 18（推荐使用 nvm 管理多版本）
- npm >= 9
- Java 21 与 Maven Wrapper（仓库已内置 `mvnw`）
- Docker / Docker Compose（用于一键启动）

克隆仓库后，建议先复制 `frontend/.env.example` 至 `frontend/.env` 并调整 `VITE_API_BASE_URL`、`VITE_WS_BASE_URL`。

## 一键启动

```bash
docker-compose up --build
```

- 后端：`http://localhost:8000/api`
- WebSocket：`ws://localhost:8000/ws`
- 前端：`http://localhost:5173`

首次执行会自动构建 Spring Boot Jar 与安装前端依赖。要停止服务可使用 `docker-compose down`。

## 分别启动前后端

### 后端

```bash
cd backend
./mvnw spring-boot:run
```

常用环境变量：

- `SERVER_PORT`：HTTP 端口，默认 8000；
- `SPRING_DATASOURCE_*`：覆盖数据库连接，默认使用 H2 内存库；
- `JWT_SECRET`、`JWT_ACCESS_TTL`、`JWT_REFRESH_TTL`：JWT 密钥与有效期；
- `AI_SOCIAL_ROOM_CODE_LENGTH`：房号长度，默认为 6。【F:backend/src/main/resources/application.yml†L1-L44】

运行测试：

```bash
./mvnw test
```

> 当前项目主要以集成测试为主，若新增复杂逻辑建议编写 `@SpringBootTest` 或 `@DataJpaTest` 保障回归。

### 前端

```bash
cd frontend
npm install
npm run dev
```

常用脚本：

- `npm run build`：生成生产构建产物；
- `npm run preview`：在本地预览构建结果；
- `npm run lint`：若后续加入 ESLint，可在此位置维护统一风格。

前端会读取 `.env` 中的 `VITE_API_BASE_URL` 与 `VITE_WS_BASE_URL` 变量；若为空会尝试根据 `VITE_API_BASE_URL` 或浏览器地址推导 WebSocket 地址。【F:frontend/src/services/realtime.ts†L9-L44】

## 账户与调试

1. 注册账号并登录获取 JWT：

   ```bash
   http POST http://localhost:8000/api/auth/register/ username=alice password=Passw0rd! display_name=Alice
   http POST http://localhost:8000/api/auth/token/ username=alice password=Passw0rd!
   ```

2. 使用 `Authorization: Bearer <token>` 调用受保护接口。
3. 通过大厅创建房间或调用 `POST /api/rooms/` 创建房间，使用 `POST /api/rooms/{id}/join/` 或 `POST /api/rooms/join-by-code/` 加入。
4. 房间内使用页面右侧聊天区验证 WebSocket 连接是否正常；若状态一直显示“离线”，请检查 `VITE_WS_BASE_URL` 与浏览器控制台错误。

## 常见陷阱

- 房间分页基于内存过滤，生产部署前需评估房间数量与内存占用，并考虑改造为数据库分页。【F:backend/src/main/java/com/aisocialgame/backend/service/RoomService.java†L94-L114】
- WebSocket 连接依赖 `token` 查询参数。若前端自定义连接逻辑，请确保登录后刷新 token 时重新建立连接，否则会遭遇 4401 关闭码。【F:backend/src/main/java/com/aisocialgame/backend/realtime/WebSocketAccessTokenInterceptor.java†L28-L92】
- `RoomRealtimeClient` 不会自动重连，组件需监听 `socketConnected` 状态并在必要时调用 `connect`；否则浏览器断网后不会恢复实时事件。【F:frontend/src/store/rooms.ts†L188-L274】
- 批量导入词库时需提供 UTF-8 编码的 CSV/JSON，且列名需匹配 `WordPairService` 解析逻辑（`keyword_a`、`keyword_b` 等）。

## 代码风格建议

- 后端遵循 Spring Boot 默认代码风格，优先通过服务层集中业务逻辑，并在控制器中保持薄层；
- 前端尽量使用组合式 API 与 TypeScript 类型定义，新增事件类型时同步更新 `src/types/rooms.ts`；
- 日志使用 `slf4j`，避免直接使用 `System.out.println`。
