#!/usr/bin/env bash
set -euo pipefail
umask 077

base_url="${ADMIN_BASE_URL:-https://localsocialgame.testhut.top}"
origin="${ADMIN_ORIGIN:-https://localsocialgame.testhut.top}"
username="${APP_ADMIN_USERNAME:-admin}"
batch_size="${MIGRATION_BATCH_SIZE:-100}"
cookie_jar="$(mktemp)"
response_file="$(mktemp)"
request_file="$(mktemp)"
proof_header_file="$(mktemp)"
authenticated=false

cleanup() {
  local status=$?
  trap - EXIT
  if [[ "$authenticated" == "true" ]]; then
    curl --disable -sS -o /dev/null -b "$cookie_jar" \
      -H "Origin: $origin" -X POST "$base_url/api/admin/auth/logout" || true
  fi
  rm -f -- "$cookie_jar" "$response_file" "$request_file" "$proof_header_file"
  exit "$status"
}
trap cleanup EXIT

json_field() {
  local field="$1"
  python3 -c 'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$field"
}

write_json_request() {
  local target="$1"
  shift
  python3 -c '
import json
import sys

names = sys.argv[1:]
values = sys.stdin.buffer.read().split(b"\0")
if values and values[-1] == b"":
    values.pop()
if len(names) != len(values):
    raise SystemExit("request field count mismatch")
payload = {name: value.decode("utf-8") for name, value in zip(names, values)}
json.dump(payload, sys.stdout, ensure_ascii=False, separators=(",", ":"))
' "$@" > "$target"
}

write_batch_request() {
  [[ "$batch_size" =~ ^[1-9][0-9]*$ ]] || { echo "MIGRATION_BATCH_SIZE must be a positive integer" >&2; exit 1; }
  printf '{"batchSize":%s}\n' "$batch_size" > "$request_file"
}

read -r -s -p "Admin password: " password
printf '\n'
printf '%s\0%s\0' "$username" "$password" | write_json_request "$request_file" username password
login_status="$(curl --disable -sS -o "$response_file" -w '%{http_code}' -c "$cookie_jar" \
  -H "Origin: $origin" -H 'Content-Type: application/json' \
  --data-binary "@$request_file" \
  "$base_url/api/admin/auth/login")"
: > "$request_file"
unset password

if [[ "$login_status" == "202" ]]; then
  challenge_id="$(json_field challengeId < "$response_file")"
  [[ -n "$challenge_id" ]] || { echo "Login challenge missing" >&2; exit 1; }
  login_state="$(json_field state < "$response_file")"
  if [[ "$login_state" == "ENROLLMENT_REQUIRED" ]]; then
    printf '%s\0' "$challenge_id" | write_json_request "$request_file" challengeId
    enrollment_status="$(curl --disable -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" \
      -H "Origin: $origin" -H 'Content-Type: application/json' \
      --data-binary "@$request_file" \
      "$base_url/api/admin/auth/enrollment/start")"
    : > "$request_file"
    [[ "$enrollment_status" == "200" ]] || { echo "TOTP enrollment start failed" >&2; exit 1; }
    manual_key="$(json_field manualKey < "$response_file")"
    [[ -n "$manual_key" ]] || { echo "TOTP enrollment key missing" >&2; exit 1; }
    echo "First-time TOTP enrollment is required. Add this key to the controlled authenticator:"
    printf '%s\n' "$manual_key"
    unset manual_key
    read -r -s -p "Enrollment TOTP code: " totp_code
    printf '\n'
    printf '%s\0%s\0' "$challenge_id" "$totp_code" | write_json_request "$request_file" challengeId code
    confirm_status="$(curl --disable -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" -c "$cookie_jar" \
      -H "Origin: $origin" -H 'Content-Type: application/json' \
      --data-binary "@$request_file" \
      "$base_url/api/admin/auth/enrollment/confirm")"
    : > "$request_file"
    unset totp_code challenge_id
    [[ "$confirm_status" == "200" ]] || { echo "TOTP enrollment failed" >&2; exit 1; }
    authenticated=true
    echo "Store these one-time recovery codes in an owner-only location:"
    python3 -c 'import json,sys; [print(code) for code in json.load(sys.stdin).get("recoveryCodes", [])]' < "$response_file"
  elif [[ "$login_state" == "TOTP_REQUIRED" ]]; then
    read -r -s -p "Login TOTP code: " totp_code
    printf '\n'
    printf '%s\0%s\0' "$challenge_id" "$totp_code" | write_json_request "$request_file" challengeId code
    verify_status="$(curl --disable -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" -c "$cookie_jar" \
      -H "Origin: $origin" -H 'Content-Type: application/json' \
      --data-binary "@$request_file" \
      "$base_url/api/admin/auth/totp/verify")"
    : > "$request_file"
    unset totp_code challenge_id
    [[ "$verify_status" == "200" ]] || { echo "TOTP login failed" >&2; exit 1; }
    authenticated=true
  else
    echo "Unsupported admin login challenge" >&2
    exit 1
  fi
elif [[ "$login_status" != "200" ]]; then
  echo "Admin login failed" >&2
  exit 1
else
  authenticated=true
fi

write_batch_request
migrate_status="$(curl --disable -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" \
  -H "Origin: $origin" -H 'Content-Type: application/json' \
  --data-binary "@$request_file" "$base_url/api/admin/billing/migrate-all")"
: > "$request_file"

if [[ "$migrate_status" == "428" ]]; then
  challenge_id="$(json_field challengeId < "$response_file")"
  read -r -s -p "Operation TOTP code: " totp_code
  printf '\n'
  printf '%s\0%s\0' "$challenge_id" "$totp_code" | write_json_request "$request_file" challengeId code
  proof_status="$(curl --disable -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" \
    -H "Origin: $origin" -H 'Content-Type: application/json' \
    --data-binary "@$request_file" \
    "$base_url/api/admin/auth/operation/verify")"
  : > "$request_file"
  unset totp_code challenge_id
  [[ "$proof_status" == "200" ]] || { echo "Operation verification failed" >&2; exit 1; }
  proof_token="$(json_field proofToken < "$response_file")"
  [[ -n "$proof_token" ]] || { echo "Operation proof missing" >&2; exit 1; }
  printf 'X-Admin-Operation-Proof: %s\n' "$proof_token" > "$proof_header_file"
  unset proof_token
  write_batch_request
  migrate_status="$(curl --disable -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" \
    -H "Origin: $origin" -H 'Content-Type: application/json' \
    --header "@$proof_header_file" \
    --data-binary "@$request_file" "$base_url/api/admin/billing/migrate-all")"
  : > "$request_file"
  : > "$proof_header_file"
fi

[[ "$migrate_status" == "200" ]] || { echo "Migration failed with HTTP $migrate_status" >&2; exit 1; }
python3 -c 'import json,sys; data=json.load(sys.stdin); print(json.dumps({k:data.get(k) for k in ("scanned","success","failed")}, ensure_ascii=False))' < "$response_file"
failed_count="$(python3 -c 'import json,sys; print(int(json.load(sys.stdin).get("failed", 0)))' < "$response_file")"
if (( failed_count > 0 )); then
  echo "Migration completed with failed=$failed_count" >&2
  exit 1
fi
