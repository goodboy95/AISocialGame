#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"
export CI=${CI:-true}
APP_DOMAIN="${APP_DOMAIN:-aisocialgame.seekerhut.com}"

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

export MYSQL_HOST="${MYSQL_HOST:-192.168.5.141}"
export MYSQL_PORT="${MYSQL_PORT:-3306}"
export MYSQL_DB="${MYSQL_DB:-aisocialgame}"
export MYSQL_ROOT_USERNAME="${MYSQL_ROOT_USERNAME:-root}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-password}"
export MYSQL_BOOTSTRAP_ENABLED="${MYSQL_BOOTSTRAP_ENABLED:-true}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-aisocialgame}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-aisocialgame_pwd}"
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-192.168.5.141}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"
export CONSUL_HTTP_ADDR="${CONSUL_HTTP_ADDR:-http://192.168.5.141:60000}"
export USER_GRPC_SERVICE_NAME="${USER_GRPC_SERVICE_NAME:-aienie-userservice-grpc}"
export BILLING_GRPC_SERVICE_NAME="${BILLING_GRPC_SERVICE_NAME:-aienie-payservice-grpc}"
export AI_GRPC_SERVICE_NAME="${AI_GRPC_SERVICE_NAME:-aienie-aiservice-grpc}"
export USER_GRPC_ADDR="${USER_GRPC_ADDR:-consul:///${USER_GRPC_SERVICE_NAME}}"
export BILLING_GRPC_ADDR="${BILLING_GRPC_ADDR:-consul:///${BILLING_GRPC_SERVICE_NAME}}"
export AI_GRPC_ADDR="${AI_GRPC_ADDR:-consul:///${AI_GRPC_SERVICE_NAME}}"
export QDRANT_HOST="${QDRANT_HOST:-http://192.168.5.141}"
export QDRANT_PORT="${QDRANT_PORT:-6333}"
export QDRANT_ENABLED="${QDRANT_ENABLED:-true}"
export SSO_USER_SERVICE_NAME="${SSO_USER_SERVICE_NAME:-aienie-userservice-http}"
export SSO_USER_SERVICE_BASE_URL="${SSO_USER_SERVICE_BASE_URL:-https://userservice.seekerhut.com}"
export SSO_CALLBACK_URL="${SSO_CALLBACK_URL:-https://${APP_DOMAIN}/sso/callback}"
export SSO_LOGIN_PATH="${SSO_LOGIN_PATH:-/sso/login}"
export SSO_REGISTER_PATH="${SSO_REGISTER_PATH:-/register}"
export USER_SERVICE_BASE_URL="${USER_SERVICE_BASE_URL:-https://userservice.seekerhut.com}"
export PAY_SERVICE_BASE_URL="${PAY_SERVICE_BASE_URL:-https://payservice.seekerhut.com}"
export AI_SERVICE_BASE_URL="${AI_SERVICE_BASE_URL:-https://aiservice.seekerhut.com}"

docker_compose_cmd() {
  if command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
  else
    echo "docker compose"
  fi
}

ensure_mysql_ready() {
  if [[ "${MYSQL_BOOTSTRAP_ENABLED}" != "true" ]]; then
    echo "Skip MySQL bootstrap (MYSQL_BOOTSTRAP_ENABLED=${MYSQL_BOOTSTRAP_ENABLED})"
    return 0
  fi

  step "Ensure MySQL database/user"
  docker run --rm --network host mysql:8.0 sh -c \
    "mysql --connect-timeout=10 --ssl-mode=DISABLED --get-server-public-key -h '${MYSQL_HOST}' -P '${MYSQL_PORT}' -u '${MYSQL_ROOT_USERNAME}' -p'${MYSQL_ROOT_PASSWORD}' <<'SQL'
CREATE DATABASE IF NOT EXISTS \`${MYSQL_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${SPRING_DATASOURCE_USERNAME}'@'%' IDENTIFIED BY '${SPRING_DATASOURCE_PASSWORD}';
ALTER USER '${SPRING_DATASOURCE_USERNAME}'@'%' IDENTIFIED BY '${SPRING_DATASOURCE_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DB}\`.* TO '${SPRING_DATASOURCE_USERNAME}'@'%';
FLUSH PRIVILEGES;
SQL"
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
  # Ensure test profile keeps using embedded H2 even when runtime MySQL env is exported.
  env -u SPRING_DATASOURCE_URL \
      -u SPRING_DATASOURCE_USERNAME \
      -u SPRING_DATASOURCE_PASSWORD \
      -u SPRING_DATASOURCE_DRIVER_CLASS_NAME \
      mvn clean test package
)

step "Frontend: install & build"
(
  cd frontend
  ensure_pnpm
  pnpm install --frozen-lockfile
  pnpm build
)

step "Docker compose pull & restart"
COMPOSE="$(docker_compose_cmd)"
ensure_mysql_ready
echo "Using shared services: MYSQL=${MYSQL_HOST}:${MYSQL_PORT} REDIS=${SPRING_DATA_REDIS_HOST}:${SPRING_DATA_REDIS_PORT} QDRANT=${QDRANT_HOST}:${QDRANT_PORT} CONSUL=${CONSUL_HTTP_ADDR}"
echo "External domains: USER=${USER_SERVICE_BASE_URL} PAY=${PAY_SERVICE_BASE_URL} AI=${AI_SERVICE_BASE_URL}"
echo "gRPC services via consul: user=${USER_GRPC_SERVICE_NAME} billing=${BILLING_GRPC_SERVICE_NAME} ai=${AI_GRPC_SERVICE_NAME}"
$COMPOSE down -v || true
$COMPOSE pull
$COMPOSE up -d

step "Wait for services"
wait_for_http "http://127.0.0.1:11030" 60
wait_for_http "http://127.0.0.1:11031/actuator/health" 60

echo "All done. Frontend: https://${APP_DOMAIN}  Backend API: https://${APP_DOMAIN}/api"
