# 本地启动与测试说明

> 以下步骤复现了本次修复期间用于验证逻辑的本地启动方式，均以仓库根目录为基准执行。默认端口：后端 `http://localhost:8100/api`，WebSocket `ws://localhost:8100/api/ws`，前端 `http://localhost:5100`（或通过 `docker-compose` 中的 `gateway` 以 `http://localhost` 访问）。

## 1. 准备后端运行环境

1. 进入后端目录并构建可运行 Jar：
   ```bash
   cd backend
   ./mvnw package
   ```
2. 使用内存 H2 数据库运行 Spring Boot（避免依赖外部 MySQL）：
   ```bash
   SPRING_DATASOURCE_URL='jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false' \
   SPRING_DATASOURCE_USERNAME=sa \
   SPRING_DATASOURCE_PASSWORD= \
   SPRING_DATASOURCE_DRIVER=org.h2.Driver \
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```
3. 等待日志提示 `Tomcat started on port 8100` 即表示后端可用。此时：
   - REST：`curl http://localhost:8100/api/meta/styles/`
   - WebSocket：`ws://localhost:8100/api/ws/rooms/{roomId}?token=<JWT>`

> 若要使用 MySQL，可删除上述 `SPRING_DATASOURCE_*` 覆盖，改由 `application.yml` 默认为 MySQL 配置。

## 2. （可选）启动前端

1. 另开终端进入 `frontend/`：
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
2. 浏览器访问 `http://localhost:5100`；需要 `frontend/.env`（可由 `.env.example` 复制）确保：
  ```ini
  VITE_API_BASE_URL=http://localhost/api
  VITE_WS_BASE_URL=ws://localhost/api/ws
  ```
3. 如需通过反向代理访问，可在根目录执行 `docker compose up --build gateway frontend backend`（或使用 `docker-compose` CLI），并在 hosts 中加入 `127.0.0.1 socialgame.seekerhut.com`，即可使用 `http://socialgame.seekerhut.com`。Compose 会先构建 `infra/docker/dev/*.Dockerfile` 中的轻量镜像，解决老版本 `docker-compose` 与新 Docker Engine 组合下出现的 `KeyError: 'ContainerConfig'` 报错。

## 3. Playwright 场景测试

> Playwright 脚本直接调用后端 REST + WebSocket，不依赖前端页面。请确保第 1 步中的后端已启动。

1. 安装测试依赖：
   ```bash
   cd tests/playwright
   npm install
   ```
2. 运行测试：
   ```bash
   npx playwright test
   ```
   - 指标：脚本会注册新用户、创建房间、添加 AI、通过 WebSocket 完成发言与投票，最终断言 `winner` 字段出现。
   - 日志与截图（如有失败）将保存在 `tests/playwright/test-results/`。

## 4. 常见端口 & 访问入口

| 服务        | 地址/端口                       | 说明                                   |
|-------------|---------------------------------|----------------------------------------|
| 后端 REST   | `http://localhost:8100/api`     | 默认上下文 `/api`                      |
| WebSocket   | `ws://localhost:8100/api/ws`    | 房间连接：`/rooms/{id}?token=JWT`      |
| 前端 Dev    | `http://localhost:5100`         | `npm run dev`                          |
| 反向代理    | `http://localhost/`             | `docker compose up gateway` 可用       |
| Playwright  | `tests/playwright` 目录下执行   | `npx playwright test`                  |

按照以上步骤即可复现本次修复所使用的启动与测试方式。若在 CI/远程环境运行，可将第 1 步的 H2 配置替换为正式数据库，并在 Playwright 前增加 `docker compose up` 或其他服务编排脚本。
