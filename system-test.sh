#!/usr/bin/env bash
set -euo pipefail

# 统一的系统测试脚本：后端单测 -> 构建并拉起容器 -> Playwright 全流程
# 可通过环境变量覆盖：
#   AISOCIAL_DATA_BASE, AISOCIAL_MYSQL_DATA_DIR, AISOCIAL_REDIS_DATA_DIR, PLAYWRIGHT_BASE_URL

root_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root_dir"

DATA_BASE="${AISOCIAL_DATA_BASE:-/var/lib/aisocialgame}"
MYSQL_DATA_DIR="${AISOCIAL_MYSQL_DATA_DIR:-$DATA_BASE/mysql}"
REDIS_DATA_DIR="${AISOCIAL_REDIS_DATA_DIR:-$DATA_BASE/redis}"
PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://socialgame.seekerhut.com}"
MAVEN_IMAGE="maven:3.9-eclipse-temurin-21"

step() { echo "== $1 =="; }

ensure_dir() {
  if ! mkdir -p "$1" 2>/dev/null; then
    sudo mkdir -p "$1"
  fi
}

ensure_dir "$root_dir/.cache/.m2"
ensure_dir "$MYSQL_DATA_DIR"
ensure_dir "$REDIS_DATA_DIR"
if command -v sudo >/dev/null 2>&1; then
  sudo chown -R 999:999 "$MYSQL_DATA_DIR" 2>/dev/null || true
  sudo chown -R 999:1000 "$REDIS_DATA_DIR" 2>/dev/null || true
  sudo chmod -R 770 "$DATA_BASE" 2>/dev/null || true
fi

export AISOCIAL_MYSQL_DATA_DIR="$MYSQL_DATA_DIR"
export AISOCIAL_REDIS_DATA_DIR="$REDIS_DATA_DIR"

step "Backend tests (Maven, profile=test, H2)"
docker run --rm \
  -v "$root_dir/backend":/workspace \
  -v "$root_dir/.cache/.m2":/root/.m2 \
  -w /workspace \
  "$MAVEN_IMAGE" \
  mvn test

step "Frontend install"
cd "$root_dir/frontend"
pnpm install --frozen-lockfile
pnpm exec playwright install --with-deps

step "Docker compose build & up"
cd "$root_dir"
COMPOSE_CMD=$(command -v docker-compose >/dev/null 2>&1 && echo docker-compose || echo "docker compose")
$COMPOSE_CMD build
$COMPOSE_CMD up -d

step "Wait for services"
wait_for_http() {
  local url="$1"; local tries="${2:-40}"
  for i in $(seq 1 "$tries"); do
    if curl -fsS "$url" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  echo "Service $url not ready after $tries attempts" >&2
  return 1
}
wait_for_http "${PLAYWRIGHT_BASE_URL}" 40
wait_for_http "${PLAYWRIGHT_BASE_URL}:8080/actuator/health" 40

step "Playwright e2e"
cd "$root_dir/frontend"
PLAYWRIGHT_BASE_URL="$PLAYWRIGHT_BASE_URL" pnpm test:e2e

echo "System tests completed. Frontend: ${PLAYWRIGHT_BASE_URL}  Backend: ${PLAYWRIGHT_BASE_URL}:8080"
