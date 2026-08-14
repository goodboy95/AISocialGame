#!/usr/bin/env bash
set -euo pipefail
umask 077

repo_root="$(cd -- "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
helper="$repo_root/scripts/admin-billing-migrate-all.sh"
fixture_dir="$(mktemp -d)"
trap 'rm -rf -- "$fixture_dir"' EXIT
mock_bin="$fixture_dir/bin"
mkdir -p "$mock_bin"

cat > "$mock_bin/curl" <<'MOCK_CURL'
#!/usr/bin/env bash
set -euo pipefail

state_file="${MOCK_CURL_STATE:?}"
violation_file="${MOCK_CURL_VIOLATION:?}"
call=0
if [[ -f "$state_file" ]]; then
  read -r call < "$state_file"
fi
call=$((call + 1))
printf '%s\n' "$call" > "$state_file"

args=("$@")
for arg in "${args[@]}"; do
  case "$arg" in
    *admin-password-sentinel*|*login-challenge-sentinel*|*123456*|*operation-challenge-sentinel*|*654321*|*proof-token-sentinel*)
      : > "$violation_file"
      echo "secret appeared in curl argv" >&2
      exit 90
      ;;
  esac
done

output_file=""
data_file=""
header_file=""
write_status=false
for ((index = 0; index < ${#args[@]}; index++)); do
  case "${args[$index]}" in
    -o)
      index=$((index + 1))
      output_file="${args[$index]}"
      ;;
    -w)
      index=$((index + 1))
      write_status=true
      ;;
    --data-binary)
      index=$((index + 1))
      [[ "${args[$index]}" == @* ]]
      data_file="${args[$index]#@}"
      ;;
    --header)
      index=$((index + 1))
      [[ "${args[$index]}" == @* ]]
      header_file="${args[$index]#@}"
      ;;
  esac
done

if [[ -n "$data_file" ]]; then
  [[ "$(stat -c '%a' "$data_file")" == "600" ]]
fi
if [[ -n "$header_file" ]]; then
  [[ "$(stat -c '%a' "$header_file")" == "600" ]]
fi

url="${args[${#args[@]} - 1]}"
status="200"
case "$call" in
  1)
    [[ "$url" == */api/admin/auth/login ]]
    body="$(<"$data_file")"
    [[ "$body" == *admin-password-sentinel* ]]
    printf '%s' '{"state":"TOTP_REQUIRED","challengeId":"login-challenge-sentinel"}' > "$output_file"
    status="202"
    ;;
  2)
    [[ "$url" == */api/admin/auth/totp/verify ]]
    body="$(<"$data_file")"
    [[ "$body" == *login-challenge-sentinel* && "$body" == *123456* ]]
    printf '%s' '{"state":"AUTHENTICATED"}' > "$output_file"
    ;;
  3)
    [[ "$url" == */api/admin/billing/migrate-all ]]
    printf '%s' '{"challengeId":"operation-challenge-sentinel"}' > "$output_file"
    status="428"
    ;;
  4)
    [[ "$url" == */api/admin/auth/operation/verify ]]
    body="$(<"$data_file")"
    [[ "$body" == *operation-challenge-sentinel* && "$body" == *654321* ]]
    printf '%s' '{"proofToken":"proof-token-sentinel"}' > "$output_file"
    ;;
  5)
    [[ "$url" == */api/admin/billing/migrate-all ]]
    header="$(<"$header_file")"
    [[ "$header" == 'X-Admin-Operation-Proof: proof-token-sentinel' ]]
    printf '%s' '{"scanned":3,"success":3,"failed":0}' > "$output_file"
    ;;
  6)
    [[ "$url" == */api/admin/auth/logout ]]
    ;;
  *)
    echo "unexpected curl call" >&2
    exit 91
    ;;
esac

if [[ "$write_status" == "true" ]]; then
  printf '%s' "$status"
fi
MOCK_CURL
chmod 700 "$mock_bin/curl"

if grep -Eq -- 'X-Admin-Operation-Proof: \$|--data[[:space:]]|python3.*"\$(password|totp_code|challenge_id|proof_token)"' "$helper"; then
  echo "helper still contains a secret-bearing argv construction" >&2
  exit 1
fi

state_file="$fixture_dir/curl-state"
violation_file="$fixture_dir/curl-argv-violation"
stdout_file="$fixture_dir/stdout"
stderr_file="$fixture_dir/stderr"

printf '%s\n' 'admin-password-sentinel' '123456' '654321' | \
  env PATH="$mock_bin:$PATH" \
    MOCK_CURL_STATE="$state_file" \
    MOCK_CURL_VIOLATION="$violation_file" \
    ADMIN_BASE_URL=https://localsocialgame.testhut.top \
    ADMIN_ORIGIN=https://localsocialgame.testhut.top \
    bash "$helper" > "$stdout_file" 2> "$stderr_file"

[[ ! -e "$violation_file" ]]
[[ "$(<"$state_file")" == "6" ]]
combined_output="$(<"$stdout_file")$(<"$stderr_file")"
for marker in admin-password-sentinel login-challenge-sentinel 123456 operation-challenge-sentinel 654321 proof-token-sentinel; do
  [[ "$combined_output" != *"$marker"* ]]
done

echo "admin migration secret argv tests: PASS"
