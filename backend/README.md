# 后端服务（Django + DRF + Channels）

本目录包含 Django 后端工程代码，负责提供 REST API、WebSocket 实时通信以及未来的 AI 能力集成。当前已落地房间管理、实时聊天等核心模块，可直接供前端大厅与房间页面联调使用。

## 目录结构

```text
apps/               # 业务应用目录
  ai/               # AI 助手配置占位
  gamecore/         # 游戏会话协调占位
  games/            # 游戏元数据占位
  rooms/            # 房间模型、业务服务、REST API、Consumers
  users/            # 自定义用户模型与认证 API
config/             # Django 项目配置（ASGI/WSGI/Settings）
manage.py           # 管理命令入口
requirements/       # 依赖声明
Dockerfile          # Docker 构建文件
```

## 环境变量

使用 `django-environ` 读取环境变量，`.env.example` 已提供推荐配置：

- `SECRET_KEY`：Django 密钥。
- `DATABASE_URL`：默认指向 `mysql://ai_social_game:ai_social_game@db:3306/ai_social_game`。
- `REDIS_URL`：Channels 使用的 Redis 连接。
- `CORS_ALLOWED_ORIGINS`：允许的前端域名，默认 `http://localhost:5173`。

## 关键依赖

- Django 5 + Django REST Framework
- Channels + channels-redis（为 WebSocket 做准备）
- djangorestframework-simplejwt：JWT 登录/刷新
- django-cors-headers：跨域支持

## 可用接口

- 认证体系
  - `POST /api/auth/register/`：注册新用户。
  - `POST /api/auth/token/`、`POST /api/auth/token/refresh/`：获取/刷新 JWT。
  - `GET /api/auth/me/`：获取当前用户信息。
- 房间管理
  - `GET /api/rooms/`：分页查询房间列表，支持 `search`、`status`、`is_private` 参数。
  - `POST /api/rooms/`：创建房间，自动生成房间号并将房主加入。
  - `GET /api/rooms/{id}/`：查看房间详情（含成员列表、房主标识、房态）。
  - `POST /api/rooms/{id}/join/`、`POST /api/rooms/{id}/leave/`：加入/退出房间。
  - `POST /api/rooms/join-by-code/`：通过房号加入房间。
  - `POST /api/rooms/{id}/start/`：房主发起游戏流程。
  - `DELETE /api/rooms/{id}/`：房主解散房间。
- 健康检查：`GET /api/health/`。

WebSocket 入口为 `ws://<host>/ws/rooms/<room_id>/?token=<jwt>`，仅允许房间成员建立连接，支持 `chat.message` 文本聊天与 `system.broadcast` 系统事件。

## 核心模块速览

- `apps/rooms/services.py`
  - `create_room`：封装房间初始化、房主入座与系统广播。
  - `join_room` / `leave_room`：处理并发校验、席位分配、房主转移。
  - `start_room` / `dissolve_room`：房主权限校验并通过 Channels 通知所有成员。
- `apps/rooms/consumers.py`
  - WebSocket 消息协议统一使用 `type` + `payload`，客户端连接后会收到 `system.sync` 房间快照。
  - 支持 `chat.message` 文本消息、`system.broadcast` 系统通知与 `ping/pong` 心跳。
- `apps/rooms/tests/`：使用 `pytest` + `pytest-django` 覆盖 REST & WS 核心流程，可作为新增功能的测试范例。

## 开发与测试

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements/dev.txt
python manage.py migrate
python manage.py runserver

# 运行自动化测试
pytest
```

测试配置基于 `config.settings.test`，使用 SQLite 与 InMemory Channel Layer，`pytest` 用例覆盖了房间创建/加入/退出、房主权限校验以及 WebSocket 消息广播。

### 本地 Channels 调试小贴士

- 需要 Redis 支撑多实例时，可使用 `docker compose up redis` 或本地安装 `redis-server` 后在 `.env` 中设置 `REDIS_URL=redis://127.0.0.1:6379/0`。
- `python manage.py runserver` 已启用 ASGI，可直接用于 WS 调试；若需要压测或与前端联调，可改用 `daphne config.asgi:application --port 8000`。
- `python manage.py shell_plus` 中可调用 `apps.rooms.services` 的函数进行手动验证，确保业务逻辑与广播一致。

> Tips：默认 `local` 配置关闭了全局权限校验，方便联调。生产环境请切换到 `config.settings.production` 并补充安全相关配置。
