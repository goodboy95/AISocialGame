#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"

export CI=${CI:-true}
APP_DOMAIN_DEFAULT="${APP_DOMAIN_DEFAULT:-localsocialgame.testhut.top}"
APP_DOMAIN="${APP_DOMAIN:-$APP_DOMAIN_DEFAULT}"

step() {
  echo "== $1 =="
}

ensure_pnpm() {
  corepack enable >/dev/null 2>&1 || true
}

env_file="$repo_root/env.local"
# shellcheck source=scripts/lib/deployment-env.sh
source "$repo_root/scripts/lib/deployment-env.sh"
step "Load env file: $(basename "$env_file")"
aisocial_load_runtime_env "$env_file"

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://localbase.testhut.top:23306/aisocialgame?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-aisocialgame}"
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-localbase.testhut.top}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-26379}"
export USER_GRPC_ADDR="${USER_GRPC_ADDR:-static://localuserservice.testhut.top:443}"
export BILLING_GRPC_ADDR="${BILLING_GRPC_ADDR:-static://localpayservice.testhut.top:443}"
export AI_GRPC_ADDR="${AI_GRPC_ADDR:-static://localaiservice.testhut.top:443}"
export USER_GRPC_NEGOTIATION_TYPE="${USER_GRPC_NEGOTIATION_TYPE:-TLS}"
export BILLING_GRPC_NEGOTIATION_TYPE="${BILLING_GRPC_NEGOTIATION_TYPE:-TLS}"
export AI_GRPC_NEGOTIATION_TYPE="${AI_GRPC_NEGOTIATION_TYPE:-TLS}"
export QDRANT_HOST="${QDRANT_HOST:-http://localbase.testhut.top}"
export QDRANT_PORT="${QDRANT_PORT:-26333}"
export QDRANT_ENABLED="${QDRANT_ENABLED:-true}"
export SSO_USER_SERVICE_BASE_URL="${SSO_USER_SERVICE_BASE_URL:-https://localuserservice.testhut.top}"
export SSO_CALLBACK_URL="${SSO_CALLBACK_URL:-https://${APP_DOMAIN}/sso/callback}"
export SSO_LOGIN_PATH="${SSO_LOGIN_PATH:-/sso/login}"
export SSO_REGISTER_PATH="${SSO_REGISTER_PATH:-/register}"
export USER_SERVICE_BASE_URL="${USER_SERVICE_BASE_URL:-https://localuserservice.testhut.top}"
export PAY_SERVICE_BASE_URL="${PAY_SERVICE_BASE_URL:-https://localpayservice.testhut.top}"
export AI_SERVICE_BASE_URL="${AI_SERVICE_BASE_URL:-https://localaiservice.testhut.top}"
export APP_EXTERNAL_GRPC_AUTH_REQUIRED="${APP_EXTERNAL_GRPC_AUTH_REQUIRED:-true}"
export APP_SECURITY_ALLOW_WEAK_RUNTIME_DEFAULTS="${APP_SECURITY_ALLOW_WEAK_RUNTIME_DEFAULTS:-false}"
export APP_SECURITY_ALLOW_PLAINTEXT_GRPC="${APP_SECURITY_ALLOW_PLAINTEXT_GRPC:-false}"

if [[ "$SPRING_DATASOURCE_URL" == jdbc:mysql://base.seekerhut.com:3306/* ]]; then
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL/base.seekerhut.com/localbase.testhut.top}"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL/:3306/:23306}"
  echo "Rewrote SPRING_DATASOURCE_URL to use localbase.testhut.top:23306 for local deployment"
fi

if [[ "$SPRING_DATASOURCE_URL" == jdbc:mysql://service.localhut.com:3306/* ]]; then
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL/service.localhut.com/localbase.testhut.top}"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL/:3306/:23306}"
  echo "Rewrote legacy SPRING_DATASOURCE_URL to use localbase.testhut.top:23306"
fi

if [[ "${SPRING_DATA_REDIS_HOST}" == "base.seekerhut.com" && "${SPRING_DATA_REDIS_PORT}" == "6379" ]]; then
  export SPRING_DATA_REDIS_HOST="localbase.testhut.top"
  export SPRING_DATA_REDIS_PORT="26379"
  echo "Rewrote SPRING_DATA_REDIS_HOST/PORT to use localbase.testhut.top:26379 for local deployment"
fi

if [[ "${SPRING_DATA_REDIS_HOST}" == "service.localhut.com" && "${SPRING_DATA_REDIS_PORT}" == "6379" ]]; then
  export SPRING_DATA_REDIS_HOST="localbase.testhut.top"
  export SPRING_DATA_REDIS_PORT="26379"
  echo "Rewrote legacy SPRING_DATA_REDIS_HOST/PORT to use localbase.testhut.top:26379"
fi

if [[ "${QDRANT_HOST}" == "http://base.seekerhut.com" && "${QDRANT_PORT}" == "6333" ]]; then
  export QDRANT_HOST="http://localbase.testhut.top"
  export QDRANT_PORT="26333"
  echo "Rewrote QDRANT_HOST/PORT to use http://localbase.testhut.top:26333 for local deployment"
fi

if [[ "${QDRANT_HOST}" == "http://service.localhut.com" && "${QDRANT_PORT}" == "6333" ]]; then
  export QDRANT_HOST="http://localbase.testhut.top"
  export QDRANT_PORT="26333"
  echo "Rewrote legacy QDRANT_HOST/PORT to use http://localbase.testhut.top:26333"
fi

aisocial_require_deployment_env_sources

case "${ENV}:${AUTH_MODE}" in
  local:password|local:totp|test:totp|production:totp) ;;
  *)
    echo "ENV/AUTH_MODE must be exactly one of local/password, local/totp, test/totp, production/totp" >&2
    exit 1
    ;;
esac

case "${APP_ADMIN_COOKIE_SECURE:-true}" in
  true|false) ;;
  *) echo "APP_ADMIN_COOKIE_SECURE must be exactly true or false" >&2; exit 1 ;;
esac
if [[ "$ENV" != "local" && "${APP_ADMIN_COOKIE_SECURE:-true}" != "true" ]]; then
  echo "APP_ADMIN_COOKIE_SECURE must be true outside ENV=local" >&2
  exit 1
fi

if [[ "$APP_SECURITY_ALLOW_WEAK_RUNTIME_DEFAULTS" != "true" ]]; then
  if [[ "$SPRING_DATASOURCE_PASSWORD" == "aisocialgame""_pwd" ]]; then
    echo "Refusing to deploy with the default database password" >&2
    exit 1
  fi
  insecure_ssl_param="use""SSL" insecure_ssl_value="false"
  insecure_key_retrieval_param="allowPublicKey""Retrieval" insecure_key_retrieval_value="true"
  if [[ "$SPRING_DATASOURCE_URL" == *"${insecure_ssl_param}=${insecure_ssl_value}"* || "$SPRING_DATASOURCE_URL" == *"${insecure_key_retrieval_param}=${insecure_key_retrieval_value}"* ]]; then
    echo "Refusing to deploy with insecure datasource URL" >&2
    exit 1
  fi
fi

if [[ "$APP_SECURITY_ALLOW_PLAINTEXT_GRPC" != "true" ]]; then
  if [[ "$USER_GRPC_NEGOTIATION_TYPE" == "PLAINTEXT" || "$BILLING_GRPC_NEGOTIATION_TYPE" == "PLAINTEXT" || "$AI_GRPC_NEGOTIATION_TYPE" == "PLAINTEXT" ]]; then
    echo "Refusing to deploy with PLAINTEXT gRPC unless APP_SECURITY_ALLOW_PLAINTEXT_GRPC=true" >&2
    exit 1
  fi
fi

docker_compose_cmd() {
  if command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
  else
    echo "docker compose"
  fi
}

wait_for_http() {
  local url="$1"
  local tries=${2:-60}
  local delay=${3:-2}
  for i in $(seq 1 "$tries"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$delay"
  done
  echo "Service $url not ready after $tries attempts" >&2
  return 1
}

step "Backend: test & package"
(
  cd backend
  aisocial_run_backend_test_command mvn clean test package
)

step "Frontend: install & build"
(
  cd frontend
  ensure_pnpm
  pnpm install --frozen-lockfile
  pnpm build
)

step "Docker compose build, pull & restart"
COMPOSE="$(docker_compose_cmd)"
echo "Using external services: datasource=${SPRING_DATASOURCE_URL} redis=${SPRING_DATA_REDIS_HOST}:${SPRING_DATA_REDIS_PORT} qdrant=${QDRANT_HOST}:${QDRANT_PORT}"
echo "External domains: USER=${USER_SERVICE_BASE_URL} PAY=${PAY_SERVICE_BASE_URL} AI=${AI_SERVICE_BASE_URL}"
echo "gRPC targets: user=${USER_GRPC_ADDR} billing=${BILLING_GRPC_ADDR} ai=${AI_GRPC_ADDR}"
$COMPOSE down -v || true
$COMPOSE pull
# frontend 镜像必须在每次部署时用最新 pnpm build 产物重建，
# 否则 up -d 会复用旧镜像导致线上仍是过期构建
$COMPOSE build frontend
$COMPOSE up -d

step "Wait for services"
export FRONTEND_PORT="${FRONTEND_PORT:-11030}"
export BACKEND_PORT="${BACKEND_PORT:-11031}"
wait_for_http "http://127.0.0.1:${FRONTEND_PORT}" 60
wait_for_http "http://127.0.0.1:${BACKEND_PORT}/actuator/health" 60

echo "All done. Frontend: https://${APP_DOMAIN}  Backend API: https://${APP_DOMAIN}/api"
