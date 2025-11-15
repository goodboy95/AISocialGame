#!/usr/bin/env bash

if [ -z "${BASH_VERSION:-}" ]; then
  if command -v bash >/dev/null 2>&1; then
    exec bash "$0" "$@"
  else
    echo "❌ 此脚本需要 bash，请先安装或使用 bash 执行（例如：sudo bash start-compose.sh）。" >&2
    exit 1
  fi
fi

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD=(docker-compose)
else
  echo "❌ 未检测到 docker compose 或 docker-compose，请先安装。" >&2
  exit 1
fi

echo "🛠 进入项目目录：${PROJECT_ROOT}"
cd "${PROJECT_ROOT}"

echo "🏗 重新构建所需镜像..."
"${COMPOSE_CMD[@]}" build

echo "🧱 编译后端代码..."
"${COMPOSE_CMD[@]}" run --rm backend bash -lc "./mvnw -DskipTests package"

echo "🧱 编译前端代码..."
"${COMPOSE_CMD[@]}" run --rm frontend bash -lc "npm install && npm run build"

echo "🚀 以最新镜像启动 docker-compose 服务..."
"${COMPOSE_CMD[@]}" up --build -d

echo "📋 当前容器状态："
"${COMPOSE_CMD[@]}" ps

echo "✅ 完成。前端入口：http://localhost/，API 位于 http://localhost/api"
