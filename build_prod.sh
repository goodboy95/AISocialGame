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

load_env_file() {
  local env_file="$1"
  if [[ ! -f "$env_file" ]]; then
    return
  fi

  step "Load env file: $(basename "$env_file")"
  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line="$raw_line"
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    if [[ -z "$line" || "$line" == \#* ]]; then
      continue
    fi

    if [[ "$line" == export[[:space:]]* ]]; then
      line="${line#export }"
      line="${line#"${line%%[![:space:]]*}"}"
    fi

    if [[ "$line" != *=* ]]; then
      continue
    fi

    local name="${line%%=*}"
    local value="${line#*=}"
    name="${name%"${name##*[![:space:]]}"}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"

    if [[ ! "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi
    if [[ ( "$value" == \"*\" && "$value" == *\" ) || ( "$value" == \'*\' && "$value" == *\' ) ]]; then
      value="${value:1:${#value}-2}"
    fi
    if [[ -z "${!name:-}" ]]; then
      export "$name=$value"
    fi
  done < "$env_file"
}

load_env_file "$repo_root/env.txt"

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3308}/${MYSQL_DB:-aisocialgame}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-aisocialgame}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-aisocialgame_pwd}"
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6381}"
export CONSUL_HTTP_ADDR="${CONSUL_HTTP_ADDR:-http://127.0.0.1:8502}"
export USER_GRPC_ADDR="${USER_GRPC_ADDR:-consul:///aienie-userservice-grpc}"
export BILLING_GRPC_ADDR="${BILLING_GRPC_ADDR:-consul:///aienie-payservice-grpc}"
export AI_GRPC_ADDR="${AI_GRPC_ADDR:-consul:///aienie-aiservice-grpc}"
export QDRANT_HOST="${QDRANT_HOST:-http://127.0.0.1}"
export QDRANT_PORT="${QDRANT_PORT:-6335}"
export QDRANT_ENABLED="${QDRANT_ENABLED:-true}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
export SSO_USER_SERVICE_NAME="${SSO_USER_SERVICE_NAME:-aienie-userservice-http}"
export SSO_CALLBACK_URL="${SSO_CALLBACK_URL:-http://aisocialgame.aienie.com/sso/callback}"
export SSO_LOGIN_PATH="${SSO_LOGIN_PATH:-/sso/login}"
export SSO_REGISTER_PATH="${SSO_REGISTER_PATH:-/register}"

docker_compose_cmd() {
  if command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
  else
    echo "docker compose"
  fi
}

wait_for_http() {
  local url="$1"; local tries=${2:-60}
  for _ in $(seq 1 "$tries"); do
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

step "Docker compose pull & restart (prod)"
COMPOSE="$(docker_compose_cmd)"
echo "Using shared services: MYSQL=${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3308} REDIS=${SPRING_DATA_REDIS_HOST}:${SPRING_DATA_REDIS_PORT} QDRANT=${QDRANT_HOST}:${QDRANT_PORT} CONSUL=${CONSUL_HTTP_ADDR}"
$COMPOSE down -v || true
$COMPOSE pull
$COMPOSE up -d

step "Wait for services"
wait_for_http "http://127.0.0.1:11030" 60
wait_for_http "http://127.0.0.1:11031/actuator/health" 60

echo "All done. Frontend: http://aisocialgame.aienie.com  Backend: http://aisocialgame.aienie.com/api"
