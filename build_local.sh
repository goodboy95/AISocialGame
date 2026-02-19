#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"
export CI=${CI:-true}

step() {
  echo "== $1 =="
}

load_env_file() {
  local env_file="$1"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    if [[ -z "${line//[[:space:]]/}" ]]; then
      continue
    fi
    if [[ "$line" =~ ^[[:space:]]*# ]]; then
      continue
    fi
    if [[ "$line" =~ ^[[:space:]]*(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=[[:space:]]*(.*)[[:space:]]*$ ]]; then
      local key="${BASH_REMATCH[2]}"
      local value="${BASH_REMATCH[3]}"
      if [[ "$value" =~ ^\"(.*)\"$ ]]; then
        value="${BASH_REMATCH[1]}"
      elif [[ "$value" =~ ^\'(.*)\'$ ]]; then
        value="${BASH_REMATCH[1]}"
      fi
      if [[ -z "${!key+x}" ]]; then
        export "$key=$value"
      fi
    fi
  done < "$env_file"
}

autoload_project_env() {
  local env_file=""
  if [[ -f "$repo_root/.env" ]]; then
    env_file="$repo_root/.env"
  elif [[ -f "$repo_root/env.txt" ]]; then
    env_file="$repo_root/env.txt"
  fi
  if [[ -n "$env_file" ]]; then
    step "Load env file: $(basename "$env_file")"
    load_env_file "$env_file"
  fi
}

ensure_pnpm() {
  corepack enable >/dev/null 2>&1 || true
}

wait_for_http() {
  local url="$1"; local tries=${2:-90}
  for _ in $(seq 1 "$tries"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "Service $url not ready after $tries attempts" >&2
  return 1
}

stop_pid_file() {
  local pid_file="$1"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      sleep 1
    fi
    rm -f "$pid_file"
  fi
}

stop_port_owner() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -ti tcp:"$port" || true)"
    if [[ -n "$pids" ]]; then
      echo "$pids" | xargs -r kill >/dev/null 2>&1 || true
      sleep 1
    fi
  elif command -v fuser >/dev/null 2>&1; then
    fuser -k "${port}/tcp" >/dev/null 2>&1 || true
    sleep 1
  fi
}

autoload_project_env

FRONTEND_PORT="${FRONTEND_PORT:-11030}"
BACKEND_PORT="${BACKEND_PORT:-11031}"
LOG_DIR="$repo_root/artifacts/local-run"
BACKEND_PID_FILE="$LOG_DIR/backend.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend.pid"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3308}/${MYSQL_DB:-aisocialgame}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-aisocialgame}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-aisocialgame_pwd}"
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6381}"
export CONSUL_HTTP_ADDR="${CONSUL_HTTP_ADDR:-http://127.0.0.1:8502}"
export QDRANT_HOST="${QDRANT_HOST:-http://127.0.0.1}"
export QDRANT_PORT="${QDRANT_PORT:-6335}"
export QDRANT_ENABLED="${QDRANT_ENABLED:-true}"
export SERVER_PORT="$BACKEND_PORT"

mkdir -p "$LOG_DIR"

step "Stop previous local services"
stop_pid_file "$BACKEND_PID_FILE"
stop_pid_file "$FRONTEND_PID_FILE"
stop_port_owner "$BACKEND_PORT"
stop_port_owner "$FRONTEND_PORT"

step "Backend: package"
(
  cd backend
  mvn clean package -DskipTests
)

step "Frontend: install & build"
(
  cd frontend
  ensure_pnpm
  pnpm install --frozen-lockfile
  pnpm build
)

backend_jar="$(ls -1 "$repo_root"/backend/target/*.jar | grep -v 'original-' | head -n 1 || true)"
if [[ -z "$backend_jar" ]]; then
  echo "No backend jar found under backend/target" >&2
  exit 1
fi

step "Start backend (java -jar)"
nohup java -jar "$backend_jar" --server.port="$BACKEND_PORT" >"$BACKEND_LOG" 2>&1 &
echo "$!" >"$BACKEND_PID_FILE"

step "Start frontend (vite preview)"
(
  cd frontend
  nohup pnpm preview --host 0.0.0.0 --port "$FRONTEND_PORT" --strictPort >"$FRONTEND_LOG" 2>&1 &
  echo "$!" >"$FRONTEND_PID_FILE"
)

step "Wait for services"
wait_for_http "http://127.0.0.1:${BACKEND_PORT}/actuator/health" 90
wait_for_http "http://127.0.0.1:${FRONTEND_PORT}" 90

echo "Local deploy complete."
echo "Frontend: http://127.0.0.1:${FRONTEND_PORT}"
echo "Backend:  http://127.0.0.1:${BACKEND_PORT}"
echo "Logs: $LOG_DIR"
