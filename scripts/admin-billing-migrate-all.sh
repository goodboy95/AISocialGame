#!/usr/bin/env bash
set -euo pipefail
umask 077

base_url="${ADMIN_BASE_URL:-https://localsocialgame.testhut.top}"
origin="${ADMIN_ORIGIN:-https://localsocialgame.testhut.top}"
username="${APP_ADMIN_USERNAME:-admin}"
batch_size="${MIGRATION_BATCH_SIZE:-100}"
cookie_jar="$(mktemp)"
response_file="$(mktemp)"
authenticated=false

cleanup() {
  local status=$?
  trap - EXIT
  if [[ "$authenticated" == "true" ]]; then
    curl -sS -o /dev/null -b "$cookie_jar" \
      -H "Origin: $origin" -X POST "$base_url/api/admin/auth/logout" || true
  fi
  rm -f -- "$cookie_jar" "$response_file"
  exit "$status"
}
trap cleanup EXIT

json_field() {
  local field="$1"
  python3 -c 'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$field"
}

read -r -s -p "Admin password: " password
printf '\n'
login_status="$(curl -sS -o "$response_file" -w '%{http_code}' -c "$cookie_jar" \
  -H "Origin: $origin" -H 'Content-Type: application/json' \
  --data "$(python3 -c 'import json,sys; print(json.dumps({"username":sys.argv[1],"password":sys.argv[2]}))' "$username" "$password")" \
  "$base_url/api/admin/auth/login")"
unset password

if [[ "$login_status" == "202" ]]; then
  challenge_id="$(json_field challengeId < "$response_file")"
  [[ -n "$challenge_id" ]] || { echo "Login challenge missing" >&2; exit 1; }
  login_state="$(json_field state < "$response_file")"
  if [[ "$login_state" == "ENROLLMENT_REQUIRED" ]]; then
    enrollment_status="$(curl -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" \
      -H "Origin: $origin" -H 'Content-Type: application/json' \
      --data "$(python3 -c 'import json,sys; print(json.dumps({"challengeId":sys.argv[1]}))' "$challenge_id")" \
      "$base_url/api/admin/auth/enrollment/start")"
    [[ "$enrollment_status" == "200" ]] || { echo "TOTP enrollment start failed" >&2; exit 1; }
    manual_key="$(json_field manualKey < "$response_file")"
    [[ -n "$manual_key" ]] || { echo "TOTP enrollment key missing" >&2; exit 1; }
    echo "First-time TOTP enrollment is required. Add this key to the controlled authenticator:"
    printf '%s\n' "$manual_key"
    unset manual_key
    read -r -s -p "Enrollment TOTP code: " totp_code
    printf '\n'
    confirm_status="$(curl -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" -c "$cookie_jar" \
      -H "Origin: $origin" -H 'Content-Type: application/json' \
      --data "$(python3 -c 'import json,sys; print(json.dumps({"challengeId":sys.argv[1],"code":sys.argv[2]}))' "$challenge_id" "$totp_code")" \
      "$base_url/api/admin/auth/enrollment/confirm")"
    unset totp_code challenge_id
    [[ "$confirm_status" == "200" ]] || { echo "TOTP enrollment failed" >&2; exit 1; }
    authenticated=true
    echo "Store these one-time recovery codes in an owner-only location:"
    python3 -c 'import json,sys; [print(code) for code in json.load(sys.stdin).get("recoveryCodes", [])]' < "$response_file"
  elif [[ "$login_state" == "TOTP_REQUIRED" ]]; then
    read -r -s -p "Login TOTP code: " totp_code
    printf '\n'
    verify_status="$(curl -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" -c "$cookie_jar" \
      -H "Origin: $origin" -H 'Content-Type: application/json' \
      --data "$(python3 -c 'import json,sys; print(json.dumps({"challengeId":sys.argv[1],"code":sys.argv[2]}))' "$challenge_id" "$totp_code")" \
      "$base_url/api/admin/auth/totp/verify")"
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

migrate_status="$(curl -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" \
  -H "Origin: $origin" -H 'Content-Type: application/json' \
  --data "{\"batchSize\":${batch_size}}" "$base_url/api/admin/billing/migrate-all")"

if [[ "$migrate_status" == "428" ]]; then
  challenge_id="$(json_field challengeId < "$response_file")"
  read -r -s -p "Operation TOTP code: " totp_code
  printf '\n'
  proof_status="$(curl -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" \
    -H "Origin: $origin" -H 'Content-Type: application/json' \
    --data "$(python3 -c 'import json,sys; print(json.dumps({"challengeId":sys.argv[1],"code":sys.argv[2]}))' "$challenge_id" "$totp_code")" \
    "$base_url/api/admin/auth/operation/verify")"
  unset totp_code challenge_id
  [[ "$proof_status" == "200" ]] || { echo "Operation verification failed" >&2; exit 1; }
  proof_token="$(json_field proofToken < "$response_file")"
  migrate_status="$(curl -sS -o "$response_file" -w '%{http_code}' -b "$cookie_jar" \
    -H "Origin: $origin" -H 'Content-Type: application/json' \
    -H "X-Admin-Operation-Proof: $proof_token" \
    --data "{\"batchSize\":${batch_size}}" "$base_url/api/admin/billing/migrate-all")"
  unset proof_token
fi

[[ "$migrate_status" == "200" ]] || { echo "Migration failed with HTTP $migrate_status" >&2; exit 1; }
python3 -c 'import json,sys; data=json.load(sys.stdin); print(json.dumps({k:data.get(k) for k in ("scanned","success","failed")}, ensure_ascii=False))' < "$response_file"
failed_count="$(python3 -c 'import json,sys; print(int(json.load(sys.stdin).get("failed", 0)))' < "$response_file")"
if (( failed_count > 0 )); then
  echo "Migration completed with failed=$failed_count" >&2
  exit 1
fi
