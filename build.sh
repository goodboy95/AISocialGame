#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"
export CI=${CI:-true}

step() {
  echo "== $1 =="
}

ensure_pnpm() {
  corepack enable >/dev/null 2>&1 || true
}

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3308}/${MYSQL_DB:-aisocialgame}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-aisocialgame}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-aisocialgame_pwd}"
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6381}"
export CONSUL_HTTP_ADDR="${CONSUL_HTTP_ADDR:-http://127.0.0.1:8502}"
export QDRANT_HOST="${QDRANT_HOST:-http://127.0.0.1}"
export QDRANT_PORT="${QDRANT_PORT:-6335}"
export QDRANT_ENABLED="${QDRANT_ENABLED:-true}"
export SSO_CALLBACK_URL="${SSO_CALLBACK_URL:-http://aisocialgame.seekerhut.com:10030/sso/callback}"

docker_compose_cmd() {
  if command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
  else
    echo "docker compose"
  fi
}

wait_for_http() {
  local url="$1"; local tries=${2:-60}
  for i in $(seq 1 "$tries"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "Service $url not ready after $tries attempts" >&2
  return 1
}

step "Backend: test & package"
(
  cd backend
  mvn clean test package
)

step "Frontend: install & build"
(
  cd frontend
  ensure_pnpm
  pnpm install --frozen-lockfile
  pnpm build
)

step "Docker compose pull & restart (test)"
COMPOSE="$(docker_compose_cmd)"
echo "Using shared services: MYSQL=${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3308} REDIS=${SPRING_DATA_REDIS_HOST}:${SPRING_DATA_REDIS_PORT} QDRANT=${QDRANT_HOST}:${QDRANT_PORT} CONSUL=${CONSUL_HTTP_ADDR}"
$COMPOSE down -v || true
$COMPOSE pull
$COMPOSE up -d

step "Wait for services"
wait_for_http "http://127.0.0.1:10030" 60
wait_for_http "http://127.0.0.1:20030/actuator/health" 60

echo "All done. Frontend: http://aisocialgame.seekerhut.com:10030  Backend: http://aisocialgame.seekerhut.com:20030"
