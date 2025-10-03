# 后端服务（Django + DRF + Channels）

本目录包含 Django 后端工程代码，负责提供 REST API、WebSocket 实时通信以及未来的 AI 能力集成。本阶段已完成基础骨架搭建与认证模块雏形。

## 目录结构

```text
apps/               # 业务应用目录
  ai/               # AI 助手配置占位
  gamecore/         # 游戏会话协调占位
  games/            # 游戏元数据占位
  rooms/            # 房间模型骨架
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

- `POST /api/auth/register/`：注册新用户。
- `POST /api/auth/token/`：获取 JWT。
- `POST /api/auth/token/refresh/`：刷新 JWT。
- `GET /api/auth/me/`：获取当前用户信息。
- `GET /api/health/`：服务健康检查。

> Tips：默认 `local` 配置关闭了全局权限校验，方便联调。生产环境请切换到 `config.settings.production` 并补充安全相关配置。
