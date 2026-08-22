#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "${1:?repo}"&&pwd -P)";out="$(cd "${2:?bundle}"&&pwd -P)";fail(){ echo "AISocialGame production assembly failed: $*" >&2;exit 2;};[[ "$out" != /&&"$out" != "$repo"&&! -L "$out" ]]||fail unsafe
put(){ local s="$1" t="$2" m="$3";[[ -f "$s"&&! -L "$s"&&! -L "$t" ]]||fail "$s";mkdir -p "$(dirname "$t")";install -m "$m" "$s" "$t";[[ "$(sha256sum "$s"|awk '{print $1}')" = "$(sha256sum "$t"|awk '{print $1}')" ]]||fail digest;}
[[ -f "$out/backend/app.jar" && ! -L "$out/backend/app.jar" ]] || fail 'flattened backend JAR is missing or unsafe'
[[ -f "$out/frontend/dist/index.html" && ! -L "$out/frontend/dist/index.html" ]] || fail 'flattened frontend dist is missing or unsafe'
put "$repo/backend/start-production-backend.sh" "$out/backend/start-production-backend.sh" 0555
put "$repo/backend/production-migration-entrypoint.sh" "$out/backend/production-migration-entrypoint.sh" 0555
put "$repo/backend/runtime-process-environment.sh" "$out/backend/runtime-process-environment.sh" 0444
put "$repo/docker/production-load-env-file.sh" "$out/docker/production-load-env-file.sh" 0555
put "$repo/frontend/nginx.conf" "$out/frontend/nginx.conf" 0444
put "$repo/ci/aisocialgame-production-runtime-compose.yml" "$out/docker-compose.yml" 0444
put "$repo/ci/production-runtime-contract.json" "$out/release/production-runtime-contract.json" 0444
put "$repo/ci/production-persistence-preflight.sh" "$out/release/production-persistence-preflight.sh" 0555
put "$repo/ci/production-migration-executor" "$out/release/production-migration-executor" 0555
python3 "$repo/ci/write-production-sql-ledger.py" "$repo" "$out/release/migrations";chmod -R a-w "$out/release/migrations"
python3 "$repo/ci/verify-production-runtime-contract.py" "$out/release/production-runtime-contract.json" "$out/docker-compose.yml" ai-social-game "$out/backend/start-production-backend.sh" "$out/release/production-migration-executor" "$out/backend/production-migration-entrypoint.sh" "$out/release/migrations/sql-ledger.json" "$out/release/migrations/production-plan.json"
python3 "$repo/ci/write-production-migration-artifacts.py" "$out" "$out/release/production-migration-artifacts.json"
chmod 0444 "$out/release/production-migration-artifacts.json"
python3 - "$out" <<'PY'
import json,pathlib,sys
r=pathlib.Path(sys.argv[1]);m=r/'.project-manifest.json'
for p in r.rglob('*'):
 if p.is_symlink():raise SystemExit(f'symlink: {p}')
v=json.loads(m.read_text(encoding='utf-8')) if m.exists() else {'format':2,'project_key':'AISocialGame'};v['files']=sorted(p.relative_to(r).as_posix() for p in r.rglob('*') if p.is_file() and p!=m);m.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
PY
chmod 0444 "$out/.project-manifest.json";echo 'Assembled signed-v4-only AISocialGame production bundle.'
