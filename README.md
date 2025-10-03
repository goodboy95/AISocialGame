# AI Social Game Platform

本项目旨在构建一个支持实时互动、AI 助手的在线社交游戏平台。本仓库采用前后端分离的结构，后端基于 Django + DRF + Channels，前端基于 Vue 3 + Vite + Element Plus，并提供 Docker Compose 一键启动能力。

## 仓库结构

```text
backend/   # Django + Channels 后端服务
frontend/  # Vue 3 + Vite 前端工程
 doc/      # 设计文档与阶段总结
```

## 快速开始

### 准备环境

1. 安装 [Docker Desktop](https://www.docker.com/) 或兼容的 Docker Engine。
2. 克隆仓库后，复制环境变量模板：

```bash
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
```

根据实际情况修改数据库、AI 接口等配置。

### 本地启动（Docker Compose）

```bash
docker-compose up --build
```

- 前端默认运行在 `http://localhost:5173`
- 后端 REST API 位于 `http://localhost:8000/api`
- 健康检查接口：`http://localhost:8000/api/health/`

首次启动会初始化 MySQL 与 Redis 容器，并安装前后端依赖。

### 直接运行后端（可选）

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements/base.txt
cp .env.example .env
python manage.py migrate
python manage.py runserver
```

### 直接运行前端（可选）

```bash
cd frontend
npm install
npm run dev
```

## 下一阶段规划

- 实现房间管理与玩家匹配 API。
- 构建 WebSocket 信令与实时聊天通道。
- 集成 AI 助手对话与游戏玩法逻辑。

更多细节请参考 `doc/` 目录中的阶段性文档。
