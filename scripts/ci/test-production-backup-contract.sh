#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd -P)"
work_dir="$(mktemp -d)"
trap 'chmod -R u+w -- "$work_dir" 2>/dev/null || true; rm -rf -- "$work_dir"' EXIT
mkdir -p "$work_dir/bundle/backend" "$work_dir/bundle/frontend/dist"
printf 'compiled-backend-placeholder\n' >"$work_dir/bundle/backend/app.jar"
printf '<!doctype html><title>compiled frontend placeholder</title>\n' >"$work_dir/bundle/frontend/dist/index.html"
bash "$repo_root/scripts/ci/assemble-aisocialgame-production-runtime.sh" "$repo_root" "$work_dir/bundle"
python3 - "$work_dir/bundle/release/production-runtime-contract.json" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["backup"] == {
    "schema_version": "aienie-production-backup-contract-v1",
    "nightly": {
        "include": ["/srv/aienie-products/ai-social-game/records"],
        "exclude": [
            {"source": "/srv/aienie-products/ai-social-game/logs", "classification": "operational-log"}
        ],
    },
}
paths = contract["backup"]["nightly"]["include"]
assert all("env" not in path and "admin" not in path and "cap" not in path for path in paths)
PY

python3 - "$work_dir/bundle/release/production-runtime-contract.json" "$work_dir/forbidden-overlay.json" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
contract["protected_config_overlay_contract"]["allowed_files"].append({
    "target_path": "docker-compose.yml",
    "file_mode": "0600",
    "owner": "runtime_identity",
    "consumers": ["backend-runtime"],
})
pathlib.Path(sys.argv[2]).write_text(json.dumps(contract), encoding="utf-8")
PY
if python3 "$repo_root/scripts/ci/verify-production-runtime-contract.py" \
  "$work_dir/forbidden-overlay.json" \
  "$work_dir/bundle/docker-compose.yml" \
  ai-social-game \
  "$work_dir/bundle/backend/start-production-backend.sh" \
  "$work_dir/bundle/release/production-migration-executor" \
  "$work_dir/bundle/backend/production-migration-entrypoint.sh" \
  "$work_dir/bundle/release/migrations/sql-ledger.json" \
  "$work_dir/bundle/release/migrations/production-plan.json" >/dev/null 2>&1; then
  printf 'forbidden protected config artifact override was accepted\n' >&2
  exit 1
fi
printf 'AISocialGame production backup contract test passed.\n'
