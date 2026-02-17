#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"
export CI=${CI:-true}

step() {
  echo "== $1 =="
}

# --- Tooling helpers -------------------------------------------------------
MAVEN_DOCKER_IMAGE="maven:3.9-eclipse-temurin-21"
DATA_BASE="${AISOCIAL_DATA_BASE:-/var/lib/aisocialgame}"
MYSQL_DATA_DIR="${AISOCIAL_MYSQL_DATA_DIR:-$DATA_BASE/mysql}"
REDIS_DATA_DIR="${AISOCIAL_REDIS_DATA_DIR:-$DATA_BASE/redis}"

ensure_dir() {
  mkdir -p "$1" 2>/dev/null || sudo mkdir -p "$1"
}

ensure_dir "$repo_root/.cache/.m2"
ensure_dir "$MYSQL_DATA_DIR"
ensure_dir "$REDIS_DATA_DIR"
if command -v sudo >/dev/null 2>&1; then
  sudo chown -R 999:999 "$MYSQL_DATA_DIR" 2>/dev/null || true
  sudo chown -R 999:1000 "$REDIS_DATA_DIR" 2>/dev/null || true
  sudo chmod -R 770 "$DATA_BASE" 2>/dev/null || true
fi
export AISOCIAL_MYSQL_DATA_DIR="$MYSQL_DATA_DIR"
export AISOCIAL_REDIS_DATA_DIR="$REDIS_DATA_DIR"

ensure_pnpm() {
  corepack enable >/dev/null 2>&1 || true
}

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

# --- Build & Test ----------------------------------------------------------

step "Backend: test & package"
(
  cd backend
  docker run --rm \
    -v "$PWD":/workspace \
    -v "$repo_root/.cache/.m2":/root/.m2 \
    -w /workspace \
    "$MAVEN_DOCKER_IMAGE" \
    mvn clean test package
)

step "Frontend: install & build"
(
  cd frontend
  ensure_pnpm
  pnpm install --frozen-lockfile
  pnpm build
)

step "Docker compose pull & restart (no local image build)"
COMPOSE="$(docker_compose_cmd)"
$COMPOSE down -v || true
$COMPOSE pull
$COMPOSE up -d

step "Wait for services"
wait_for_http "http://aisocialgame.seekerhut.com:10030" 40
wait_for_http "http://aisocialgame.seekerhut.com:20030/actuator/health" 40

echo "All done. Frontend: http://aisocialgame.seekerhut.com:10030  Backend: http://aisocialgame.seekerhut.com:20030"
