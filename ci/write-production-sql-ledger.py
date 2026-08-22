#!/usr/bin/env python3
import hashlib,json,pathlib,shutil,sys
repo=pathlib.Path(sys.argv[1]).resolve(); out=pathlib.Path(sys.argv[2]); sql_out=out/'sql'; sql_out.mkdir(parents=True,exist_ok=True)
ordered=[('baseline','schema.sql'),('upgrade','20260519_performance_stability.sql'),('upgrade','20260810_admin_totp_auth.sql')]
entries=[]
for ordinal,(kind,name) in enumerate(ordered,1):
    src=repo/'backend/sql'/name
    if not src.is_file() or src.is_symlink(): raise SystemExit(f'unsafe SQL migration: {name}')
    data=src.read_bytes(); target=sql_out/name; shutil.copyfile(src,target)
    entries.append({'ordinal':ordinal,'kind':kind,'path':f'release/migrations/sql/{name}','sha256':hashlib.sha256(data).hexdigest()})
chain=hashlib.sha256(''.join(x['sha256'] for x in entries).encode('ascii')).hexdigest()
ledger={'schema_version':'aienie-production-sql-ledger-v1','canonical_component_id':'ai-social-game','authorization':{'minimum_release_manifest_version':4,'outer_signature_required':True},'execution_plans':{'fresh-empty-schema':[1],'existing-legacy-schema':[2,3]},'plan_selection':'signed-explicit-no-auto-detection','checkpoint':{'phase':'prestart','receipt_authority':'control-plane','required_fields':['ledger_sha256','selected_execution_plan','database_backup_receipt','applied_ordinals','post_schema_validation']},'ledger_sha256':chain,'entries':entries}
(out/'sql-ledger.json').write_text(json.dumps(ledger,indent=2)+'\n',encoding='utf-8',newline='\n')
