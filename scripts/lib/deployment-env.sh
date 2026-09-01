#!/usr/bin/env bash

# Runtime values consumed by build preflight and by the backend container must
# come from env.local. Clearing inherited copies prevents a host shell from
# satisfying validation with values that Docker Compose never passes through.
AISOCIAL_RUNTIME_ENV_NAMES=(
  ENV
  AUTH_MODE
  SPRING_DATASOURCE_URL
  SPRING_DATASOURCE_USERNAME
  SPRING_DATASOURCE_PASSWORD
  SPRING_JPA_HIBERNATE_DDL_AUTO
  SPRING_PROFILES_ACTIVE
  SPRING_DATA_REDIS_HOST
  SPRING_DATA_REDIS_PORT
  USER_GRPC_ADDR
  BILLING_GRPC_ADDR
  AI_GRPC_ADDR
  USER_GRPC_NEGOTIATION_TYPE
  BILLING_GRPC_NEGOTIATION_TYPE
  AI_GRPC_NEGOTIATION_TYPE
  QDRANT_HOST
  QDRANT_PORT
  QDRANT_ENABLED
  SSO_USER_SERVICE_BASE_URL
  SSO_CALLBACK_URL
  SSO_LOGIN_PATH
  SSO_REGISTER_PATH
  USER_SERVICE_BASE_URL
  PAY_SERVICE_BASE_URL
  AI_SERVICE_BASE_URL
  APP_EXTERNAL_GRPC_AUTH_REQUIRED
  APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN
  APP_EXTERNAL_USERSERVICE_JWT_CALLER_ID
  APP_EXTERNAL_USERSERVICE_JWT_ISSUER
  APP_EXTERNAL_USERSERVICE_JWT_SECRET
  APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE
  APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS
  APP_EXTERNAL_USERSERVICE_JWT_SCOPES
  APP_EXTERNAL_PAYSERVICE_JWT
  APP_EXTERNAL_AISERVICE_HMAC_CALLER
  APP_EXTERNAL_AISERVICE_HMAC_SECRET
  APP_SECURITY_ALLOW_WEAK_RUNTIME_DEFAULTS
  APP_SECURITY_ALLOW_PLAINTEXT_GRPC
  APP_DEMO_SEED_ENABLED
  APP_ADMIN_USERNAME
  APP_ADMIN_PASSWORD_HASH
  APP_ADMIN_DISPLAY_NAME
  APP_ADMIN_OPERATOR_USER_ID
  APP_ADMIN_COOKIE_SECURE
  APP_ADMIN_SESSION_MINUTES
  APP_ADMIN_SESSION_IDLE_MINUTES
  APP_ADMIN_RECOVERY_SESSION_MINUTES
  APP_ADMIN_RECOVERY_SESSION_IDLE_MINUTES
  ADMIN_TOTP_ENCRYPTION_KEYS
  ADMIN_TOTP_ACTIVE_KEY_VERSION
)

AISOCIAL_BACKEND_TEST_ENV_NAMES=(
  "${AISOCIAL_RUNTIME_ENV_NAMES[@]}"
  SPRING_DATASOURCE_DRIVER_CLASS_NAME
  SPRING_CONFIG_ADDITIONAL_LOCATION
  SPRING_CONFIG_IMPORT
  SPRING_CONFIG_LOCATION
)

declare -Ag AISOCIAL_ENV_FILE_KEYS=()

aisocial_clear_inherited_runtime_env() {
  local name
  for name in "${AISOCIAL_RUNTIME_ENV_NAMES[@]}"; do
    unset "$name"
  done
}

aisocial_validate_env_file() {
  local env_file="$1"
  if [[ ! -f "$env_file" || -L "$env_file" ]]; then
    echo "Missing regular env.local file; copy env.example to env.local and populate it before deployment" >&2
    return 1
  fi

  local env_mode
  env_mode="$(stat -c '%a' "$env_file")"
  if [[ "$env_mode" != "600" ]]; then
    echo "env.local must have mode 0600 (current mode: $env_mode)" >&2
    return 1
  fi
}

aisocial_load_runtime_env() {
  local env_file="$1"
  aisocial_validate_env_file "$env_file" || return 1
  aisocial_clear_inherited_runtime_env
  AISOCIAL_ENV_FILE_KEYS=()

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line="${raw_line%$'\r'}"
    line="${line#"${line%%[![:space:]]*}"}"
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
    local raw_value="$value"
    name="${name%"${name##*[![:space:]]}"}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"

    if [[ ! "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Invalid env key in $env_file" >&2
      return 1
    fi
    if [[ "$name" == "APP_EXTERNAL_USERSERVICE_JWT_SECRET" ]]; then
      if [[ "$raw_value" != "$value" || "$value" == \"* || "$value" == *\" || "$value" == \'* || "$value" == *\' ]]; then
        echo "APP_EXTERNAL_USERSERVICE_JWT_SECRET must be an unquoted value without boundary whitespace" >&2
        return 1
      fi
    fi
    if [[ "$name" == "APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN" && "$raw_value" != "$value" ]]; then
      echo "APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN is legacy and must be absent or empty" >&2
      return 1
    fi
    if [[ ( "$value" == \"*\" && "$value" == *\" ) || ( "$value" == \'*\' && "$value" == *\' ) ]]; then
      value="${value:1:${#value}-2}"
    fi

    AISOCIAL_ENV_FILE_KEYS["$name"]=1
    export "$name=$value"
  done < "$env_file"
}

aisocial_validate_userservice_jwt_contract() {
  if [[ -n "${APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN:-}" ]]; then
    echo "APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN is legacy and must be absent or empty" >&2
    return 1
  fi
  if [[ "${APP_EXTERNAL_GRPC_AUTH_REQUIRED:-true}" != "true" ]]; then
    return 0
  fi

  aisocial_require_env_file_vars \
    APP_EXTERNAL_USERSERVICE_JWT_CALLER_ID \
    APP_EXTERNAL_USERSERVICE_JWT_ISSUER \
    APP_EXTERNAL_USERSERVICE_JWT_SECRET \
    APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE \
    APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS \
    APP_EXTERNAL_USERSERVICE_JWT_SCOPES || return 1

  if [[ "$APP_EXTERNAL_USERSERVICE_JWT_CALLER_ID" != "aisocialgame" ||
        "$APP_EXTERNAL_USERSERVICE_JWT_ISSUER" != "aisocialgame" ]]; then
    echo "UserService caller JWT identity must be aisocialgame" >&2
    return 1
  fi
  if [[ "$APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE" != "aienie-userservice-grpc" ]]; then
    echo "UserService caller JWT audience is not canonical" >&2
    return 1
  fi
  if [[ ! "$APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS" =~ ^[0-9]+$ ]] ||
     (( APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS < 30 || APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS > 900 )); then
    echo "UserService caller JWT TTL must be between 30 and 900 seconds" >&2
    return 1
  fi
  if [[ "$APP_EXTERNAL_USERSERVICE_JWT_SCOPES" != "user.auth.session.read,user.directory.read,user.ban.read,user.ban.write" ]]; then
    echo "UserService caller JWT scopes violate least privilege" >&2
    return 1
  fi

  local secret="$APP_EXTERNAL_USERSERVICE_JWT_SECRET"
  local normalized_secret="${secret^^}"
  local secret_bytes
  secret_bytes="$(LC_ALL=C printf '%s' "$secret" | wc -c | tr -d '[:space:]')"
  if (( secret_bytes < 32 || secret_bytes > 4096 )) ||
     [[ "$secret" != "${secret#"${secret%%[![:space:]]*}"}" ||
        "$secret" != "${secret%"${secret##*[![:space:]]}"}" ||
        "$normalized_secret" == *REPLACE* || "$normalized_secret" == *CHANGE_ME* ||
        "$normalized_secret" == *CHANGE-ME* || "$normalized_secret" == *CHANGEME* ||
        "$normalized_secret" == *PLACEHOLDER* ||
        "$normalized_secret" == \<* || "$normalized_secret" == *\> ]]; then
    echo "UserService caller JWT secret is invalid" >&2
    return 1
  fi
  if LC_ALL=C printf '%s' "$secret" | grep -q '[[:cntrl:]]'; then
    echo "UserService caller JWT secret contains a control character" >&2
    return 1
  fi
}

aisocial_require_env_file_vars() {
  local missing=()
  local name
  for name in "$@"; do
    if [[ -z "${AISOCIAL_ENV_FILE_KEYS[$name]+present}" || -z "${!name:-}" ]]; then
      missing+=("$name")
    fi
  done
  if (( ${#missing[@]} > 0 )); then
    echo "Missing required env.local variables: ${missing[*]}" >&2
    return 1
  fi
}

aisocial_require_deployment_env_sources() {
  aisocial_require_env_file_vars \
    SPRING_DATASOURCE_PASSWORD ENV AUTH_MODE APP_ADMIN_PASSWORD_HASH SSO_CALLBACK_URL || return 1

  if [[ "${APP_EXTERNAL_GRPC_AUTH_REQUIRED:-true}" == "true" ]]; then
    aisocial_require_env_file_vars \
      APP_EXTERNAL_PAYSERVICE_JWT \
      APP_EXTERNAL_AISERVICE_HMAC_CALLER \
      APP_EXTERNAL_AISERVICE_HMAC_SECRET || return 1
  fi

  aisocial_validate_userservice_jwt_contract || return 1

  if [[ "${AUTH_MODE:-}" == "totp" ]]; then
    aisocial_require_env_file_vars ADMIN_TOTP_ENCRYPTION_KEYS ADMIN_TOTP_ACTIVE_KEY_VERSION || return 1
  fi
}

aisocial_run_backend_test_command() {
  local env_args=()
  local name
  for name in "${AISOCIAL_BACKEND_TEST_ENV_NAMES[@]}"; do
    env_args+=(-u "$name")
  done
  env "${env_args[@]}" "$@"
}
