# 后端服务（Django + DRF + Channels）

本目录包含 Django 后端工程代码，负责提供 REST API、WebSocket 实时通信以及未来的 AI 能力集成。当前已落地房间管理、实时聊天、"谁是卧底" 与 "狼人杀" 两大玩法，可直接供前端大厅与房间页面联调使用。

## 目录结构

```text
apps/               # 业务应用目录
  ai/               # AI 策略与助手配置
  gamecore/         # 游戏会话模型、引擎基类与调度服务
  games/            # 游戏元数据、词库与具体玩法实现
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
- `CORS_ALLOW_ALL_ORIGINS`：是否放开 CORS 限制，默认 `True`。
- `CORS_ALLOWED_ORIGINS`：当未放开限制时允许的前端域名列表，默认 `http://localhost:5173`。
- `CORS_ALLOW_CREDENTIALS`：是否允许跨域携带认证信息，放开限制时默认 `False`，其他情况下默认 `True`。

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
- `GET /api/rooms/{id}/`：查看房间详情（含成员列表、房主标识、房态与当前游戏会话）。
  - `POST /api/rooms/{id}/join/`、`POST /api/rooms/{id}/leave/`：加入/退出房间。
  - `POST /api/rooms/join-by-code/`：通过房号加入房间。
- `POST /api/rooms/{id}/start/`：房主发起游戏流程，自动补齐 AI 玩家并根据房间配置选择“谁是卧底”或“狼人杀”引擎。
  - `DELETE /api/rooms/{id}/`：房主解散房间。
- 健康检查：`GET /api/health/`。

WebSocket 入口为 `ws://<host>/ws/rooms/<room_id>/?token=<jwt>`，仅允许房间成员建立连接，支持 `system.sync` 快照、`system.broadcast` 系统事件、`chat.message` 聊天以及 `game.event` 游戏状态推送。

## 核心模块速览

- `apps/rooms/services.py`
  - `create_room`：封装房间初始化、房主入座与系统广播。
  - `join_room` / `leave_room`：处理并发校验、席位分配、房主转移。
  - `start_room`：自动补齐 AI 玩家、创建游戏会话并广播初始状态。
  - `dissolve_room`：房主权限校验并通过 Channels 通知所有成员。
- `apps/gamecore/engine.py`：定义 `BaseGameEngine`、`EnginePhase` 与 `GameEvent` 数据模型。
- `apps/gamecore/services.py`：维护 `GameSession`，负责启动/更新引擎并通过 `game.event` 向前端推送状态。
- `apps/games/models.py`：提供 `WordPair` 词库模型，支持按主题/难度随机抽词。
- `apps/games/undercover/engine.py`：实现“谁是卧底”状态机，涵盖发言轮转、投票计票、平局重投、胜负判定及 AI 自动行为。
- `apps/games/werewolf/engine.py`：实现“狼人杀”多阶段状态机，处理夜间行动（狼人击杀、预言家查验、女巫解救/投毒）、白天发言与投票、胜负判定及私密信息透出。
- `apps/ai/services.py`：封装 AI 玩家昵称生成、“谁是卧底”与“狼人杀”两套启发式策略（发言、投票、夜间行动决策）。
- `apps/rooms/models.RoomPlayer`：新增 `has_used_skill` 字段，用于记录一次性技能使用情况，便于引擎协同。
- `apps/rooms/consumers.py`：WebSocket 协议统一使用 `type` + `payload`，支持聊天、系统广播、游戏事件与 `ping/pong` 心跳。
- `apps/.../tests/`：`pytest` + `pytest-django` 覆盖 REST、WS 与游戏引擎核心流程，可作为新增功能的测试范例。

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

测试配置基于 `config.settings.test`，使用 SQLite 与 InMemory Channel Layer，`pytest` 用例覆盖房间创建/加入/退出、房主权限校验、WebSocket 消息广播以及“谁是卧底”“狼人杀”核心回合逻辑。

### 本地 Channels 调试小贴士

- 需要 Redis 支撑多实例时，可使用 `docker compose up redis` 或本地安装 `redis-server` 后在 `.env` 中设置 `REDIS_URL=redis://127.0.0.1:6379/0`。
- `python manage.py runserver` 已启用 ASGI，可直接用于 WS 调试；若需要压测或与前端联调，可改用 `daphne config.asgi:application --port 8000`。
- `python manage.py shell_plus` 中可调用 `apps.rooms.services.start_room`、`apps.gamecore.services.handle_room_event` 模拟发言/投票流程。
- `apps/games/models.WordPair` 提供词库维护能力，可用于导入或调试新的词条。

> Tips：默认 `local` 配置关闭了全局权限校验，方便联调。生产环境请切换到 `config.settings.production` 并补充安全相关配置。
