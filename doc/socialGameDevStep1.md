# AI 社交游戏平台开发方案 — 步骤一：基础框架与环境搭建

## 1. 目标概述
在设计文档的总体架构指导下，完成后端（Django + DRF + Channels）与前端（Vue 3 + Vite + Element Plus）的基础项目初始化，并通过 Docker Compose 打通本地一键启动链路，为后续房间、实时通信与游戏功能开发奠定统一基础。

## 2. 范围与成果物
- Django 项目骨架、基础配置、用户认证模块雏形。
- Channels 与 ASGI 配置可运行，具备 WebSocket 连接能力（尚无业务逻辑）。
- Vue 3 + Vite 前端工程初始化，集成 Element Plus 与基础路由、状态管理脚手架。
- Docker Compose 定义后端、前端、数据库（MySQL）与可选 Nginx 服务，支持本地快速启动。
- Git 仓库基础结构遵循设计文档建议的目录布局。

## 3. 详细任务拆分
### 3.1 后端初始化
1. 创建 Django 项目 `config` 与核心 app 目录（`apps/users`, `apps/rooms`, `apps/gamecore`, `apps/games`, `apps/ai`）。
2. 配置 Django REST Framework、djangorestframework-simplejwt（或自选 JWT 方案）与基础中间件。
3. 配置 Channels：
   - 在 `config/asgi.py` 中加载 `ProtocolTypeRouter`、`AuthMiddlewareStack`。
   - 引入 Redis Channel Layer 的可配置项（环境变量控制）。
4. 在 `settings/base.py` 中抽离公共设置，使用多环境配置（`local.py`, `production.py`）。
5. 定义自定义用户模型或扩展 Profile（根据设计文档的用户资料要求），实现注册/登录 REST API 雏形。

### 3.2 数据库与迁移管理
1. 集成 MySQL 驱动（`mysqlclient` 或 `asyncmy`），在 `.env` 中维护数据库连接信息。
2. 配置 Django `DATABASES`，准备初始迁移脚本。
3. 建立基础模型：用户、房间（仅字段骨架，后续补充）、玩家关系（可留空）。
4. 运行迁移并验证 Docker 容器中可连接数据库。

### 3.3 前端初始化
1. 使用 `pnpm`/`npm` 创建 Vite + Vue3 + TypeScript 项目。
2. 集成 Element Plus、Vue Router、Pinia（或其他状态管理库）。
3. 搭建全局样式、主题色、响应式断点方案。
4. 预置页面结构与路由占位：
   - `/login`、`/register`
   - `/lobby`
   - `/room/:id`
5. 准备 REST API 封装与 WebSocket 客户端模块的空实现（接口定义、待实现函数）。

### 3.4 Docker 与本地环境
1. 在根目录准备 `docker-compose.yml`：
   - `backend` 服务使用设计文档中给出的 `Dockerfile` 片段，补全命令。
   - `frontend` 服务运行 `npm install && npm run dev`（开发阶段）或 `npm run build && serve`（生产阶段）。
   - `db` 服务使用 MySQL 8，暴露端口与数据卷。
   - 可选 `redis`（未来 Channels 使用）。
   - 可选 `nginx`（后续部署阶段再启用）。
2. 定义 `.env.example` 与 `backend/.env`, `frontend/.env` 模板，包含数据库、OpenAI 兼容 API 配置。
3. 验证本地执行 `docker-compose up` 后后端、前端、数据库可启动，REST 健康检查接口可访问。

## 4. 关键技术点与实现建议
- **用户认证**：优先确定是否使用 Django 自定义用户模型，避免后期迁移困难。
- **配置管理**：使用 `django-environ`/`pydantic` 读取环境变量，保持 Docker 与本地一致性。
- **代码风格**：集成 `black`, `isort`, `flake8`, `pre-commit`，确保后续团队协作质量。
- **前端组件库**：配置 Element Plus 自动按需引入（`unplugin-vue-components` + `ElementPlusResolver`）。
- **接口联调**：提供后端 `/api/health/` 健康检查接口，前端在 `.env` 统一配置 `VITE_API_BASE`。

## 5. 验收标准
- `docker-compose up` 成功启动后端、前端、数据库，无明显错误日志。
- REST 健康接口返回 200，Channels WebSocket 可建立握手。
- 前端可访问登录页和大厅占位页，控制台无严重错误。
- Git 仓库已提交基础目录结构与 README 更新。

## 6. 里程碑与时间预估
- **第 1 周**：后端 Django + Channels + JWT 基础搭建完成。
- **第 2 周**：前端 Vue3 工程、路由和 UI 底座完成。
- **第 2 周末**：Docker Compose 验证、文档与初始化提交。

> 完成步骤一后，即可进入房间系统与实时通信开发阶段。
