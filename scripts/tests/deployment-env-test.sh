#!/usr/bin/env bash
set -euo pipefail
umask 077

repo_root="$(cd -- "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/lib/deployment-env.sh
source "$repo_root/scripts/lib/deployment-env.sh"

# Git for Windows does not emulate POSIX chmod bits. Keep a test-local mode map
# so the production validator still exercises both owner-only and rejection paths.
if [[ -n "${MSYSTEM:-}" ]]; then
  declare -A AISOCIAL_TEST_FILE_MODES=()
  chmod() {
    AISOCIAL_TEST_FILE_MODES["$2"]="$1"
  }
  stat() {
    if [[ "${1:-}" == "-c" && "${2:-}" == "%a" ]]; then
      printf '%s\n' "${AISOCIAL_TEST_FILE_MODES[$3]:-600}"
      return 0
    fi
    /usr/bin/stat "$@"
  }
fi

fixture_dir="$(mktemp -d)"
trap 'rm -rf -- "$fixture_dir"' EXIT
missing_file="$fixture_dir/missing.env"
complete_file="$fixture_dir/complete.env"

write_common_fixture() {
  local target="$1"
  {
    printf '%s\n' \
      'ENV=local' \
      'AUTH_MODE=totp' \
      'SPRING_DATASOURCE_PASSWORD=file-database-value' \
      'SPRING_JPA_HIBERNATE_DDL_AUTO=validate' \
      'SPRING_PROFILES_ACTIVE=production' \
      'SSO_CALLBACK_URL=https://localsocialgame.testhut.top/sso/callback' \
      'ADMIN_TOTP_ENCRYPTION_KEYS=v1:file-keyring-value' \
      'ADMIN_TOTP_ACTIVE_KEY_VERSION=v1' \
      'APP_EXTERNAL_GRPC_AUTH_REQUIRED=true' \
      'APP_EXTERNAL_USERSERVICE_JWT_CALLER_ID=aisocialgame' \
      'APP_EXTERNAL_USERSERVICE_JWT_ISSUER=aisocialgame' \
      'APP_EXTERNAL_USERSERVICE_JWT_SECRET=aisocialgame-userservice-test-secret-32-bytes' \
      'APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE=aienie-userservice-grpc' \
      'APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS=300' \
      'APP_EXTERNAL_USERSERVICE_JWT_SCOPES=user.auth.session.read,user.directory.read,user.ban.read,user.ban.write' \
      'APP_EXTERNAL_PAYSERVICE_JWT=file-pay-token' \
      'APP_EXTERNAL_AISERVICE_HMAC_CALLER=file-caller' \
      'APP_EXTERNAL_AISERVICE_HMAC_SECRET=file-hmac-value' \
      'APP_DEMO_SEED_ENABLED=true'
  } > "$target"
  chmod 600 "$target"
}

write_common_fixture "$missing_file"
cp "$missing_file" "$complete_file"
printf '%s\n' 'APP_ADMIN_PASSWORD_HASH=file-password-hash' >> "$complete_file"
chmod 600 "$complete_file"

(
  export ENV=production AUTH_MODE=totp
  export SPRING_DATASOURCE_PASSWORD=host-database-value
  export SSO_CALLBACK_URL=https://host.invalid/sso/callback
  export APP_ADMIN_PASSWORD_HASH=host-password-hash
  export ADMIN_TOTP_ENCRYPTION_KEYS=v1:host-keyring-value
  export ADMIN_TOTP_ACTIVE_KEY_VERSION=v1
  export APP_EXTERNAL_GRPC_AUTH_REQUIRED=true
  export APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN=host-user-token
  export APP_EXTERNAL_USERSERVICE_JWT_SECRET=host-user-jwt-secret-that-must-not-win
  export APP_EXTERNAL_PAYSERVICE_JWT=host-pay-token
  export APP_EXTERNAL_AISERVICE_HMAC_CALLER=host-caller
  export APP_EXTERNAL_AISERVICE_HMAC_SECRET=host-hmac-value

  aisocial_load_runtime_env "$missing_file"
  if aisocial_require_deployment_env_sources 2>/dev/null; then
    echo "host-only required value incorrectly satisfied env.local contract" >&2
    exit 1
  fi
  [[ -z "${APP_ADMIN_PASSWORD_HASH+x}" ]]
)

chmod 644 "$complete_file"
if aisocial_validate_env_file "$complete_file" 2>/dev/null; then
  echo "env.local mode check accepted a non-owner-only file" >&2
  exit 1
fi
chmod 600 "$complete_file"

aisocial_load_runtime_env "$complete_file"
aisocial_require_deployment_env_sources
[[ "$ENV" == "local" ]]
[[ "$APP_ADMIN_PASSWORD_HASH" == "file-password-hash" ]]
[[ "$SPRING_JPA_HIBERNATE_DDL_AUTO" == "validate" ]]

expect_user_jwt_rejected() {
  local expression="$1"
  local replacement="$2"
  local fixture="$fixture_dir/rejected-$RANDOM.env"
  cp "$complete_file" "$fixture"
  sed -i "s|$expression|$replacement|" "$fixture"
  chmod 600 "$fixture"
  if (aisocial_load_runtime_env "$fixture" && aisocial_require_deployment_env_sources) 2>/dev/null; then
    echo "invalid UserService caller JWT deployment configuration was accepted" >&2
    exit 1
  fi
}

expect_user_jwt_rejected \
  'APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE=aienie-userservice-grpc' \
  'APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE=wrong-audience'
expect_user_jwt_rejected \
  'APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS=300' \
  'APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS=901'
expect_user_jwt_rejected \
  'APP_EXTERNAL_USERSERVICE_JWT_SCOPES=user.auth.session.read,user.directory.read,user.ban.read,user.ban.write' \
  'APP_EXTERNAL_USERSERVICE_JWT_SCOPES=user.auth.session.read,user.directory.read,user.ban.read,user.ban.write,user.preference.read'
expect_user_jwt_rejected \
  'APP_EXTERNAL_USERSERVICE_JWT_SECRET=aisocialgame-userservice-test-secret-32-bytes' \
  'APP_EXTERNAL_USERSERVICE_JWT_SECRET= weak-secret-with-boundary-whitespace'

legacy_file="$fixture_dir/legacy.env"
cp "$complete_file" "$legacy_file"
printf '%s\n' 'APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN=legacy-static-token' >> "$legacy_file"
chmod 600 "$legacy_file"
if (aisocial_load_runtime_env "$legacy_file" && aisocial_require_deployment_env_sources) 2>/dev/null; then
  echo "legacy UserService static token was accepted" >&2
  exit 1
fi

legacy_whitespace_file="$fixture_dir/legacy-whitespace.env"
cp "$complete_file" "$legacy_whitespace_file"
printf '%s\n' 'APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN=   ' >> "$legacy_whitespace_file"
chmod 600 "$legacy_whitespace_file"
if (aisocial_load_runtime_env "$legacy_whitespace_file" && aisocial_require_deployment_env_sources) 2>/dev/null; then
  echo "boundary-whitespace legacy UserService static token was accepted" >&2
  exit 1
fi

aisocial_run_backend_test_command bash -c '
  set -euo pipefail
  for name in "$@"; do
    [[ ! -v "$name" ]]
  done
' _ "${AISOCIAL_BACKEND_TEST_ENV_NAMES[@]}"
[[ "$SPRING_JPA_HIBERNATE_DDL_AUTO" == "validate" ]]
[[ "$APP_ADMIN_PASSWORD_HASH" == "file-password-hash" ]]

env -i PATH="$PATH" sh -c '
  set -eu
  loader="$1"
  shift
  . "$loader"
  test "$ENV" = local
  test "$AUTH_MODE" = totp
  test "$APP_ADMIN_PASSWORD_HASH" = file-password-hash
  test "$SPRING_JPA_HIBERNATE_DDL_AUTO" = validate
  test "$SPRING_PROFILES_ACTIVE" = production
  test "$ADMIN_TOTP_ENCRYPTION_KEYS" = v1:file-keyring-value
  test "$APP_EXTERNAL_AISERVICE_HMAC_SECRET" = file-hmac-value
' _ "$repo_root/docker/load-env-file.sh" "$complete_file"

echo "deployment env contract tests: PASS"
