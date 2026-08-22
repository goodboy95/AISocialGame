#!/bin/sh
set -eu
JAVA_HOME=/opt/java/openjdk; PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; export JAVA_HOME PATH
[ -r /app/bin/runtime-process-environment.sh ] || { echo 'runtime process policy unavailable' >&2; exit 1; }
. /app/bin/runtime-process-environment.sh; clear_process_overrides
[ "$#" -eq 1 ] || { echo 'AISocialGame production launcher requires one env file' >&2; exit 64; }
[ -r /app/bin/production-load-env-file.sh ] || { echo 'production env loader unavailable' >&2; exit 1; }
. /app/bin/production-load-env-file.sh "$1"
clear_process_overrides
[ "${ENV:-}" = production ] || { echo 'AISocialGame production ENV is required' >&2; exit 1; }
export SPRING_PROFILES_ACTIVE=production SERVER_ADDRESS=0.0.0.0 SERVER_PORT=20030
export SPRING_DATASOURCE_URL='jdbc:mysql://base.seekerhut.com:13306/aisocialgame?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=false&serverTimezone=UTC'
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
export SPRING_DATA_REDIS_HOST=base.seekerhut.com SPRING_DATA_REDIS_PORT=16379 SPRING_DATA_REDIS_SSL_ENABLED=true
export QDRANT_HOST=https://base.seekerhut.com QDRANT_PORT=16333
export USER_GRPC_ADDR=static://userservice.seekerhut.com:12001 USER_GRPC_NEGOTIATION_TYPE=TLS
export BILLING_GRPC_ADDR=static://payservice.seekerhut.com:12021 BILLING_GRPC_NEGOTIATION_TYPE=TLS
export AI_GRPC_ADDR=static://aiservice.seekerhut.com:12011 AI_GRPC_NEGOTIATION_TYPE=TLS
export SSO_USER_SERVICE_BASE_URL=https://userservice.seekerhut.com
export SSO_CALLBACK_URL=https://socialgame.seekerhut.com/sso/callback
export APP_CORS_ALLOWED_ORIGINS=https://socialgame.seekerhut.com
export APP_SECURITY_ALLOW_WEAK_RUNTIME_DEFAULTS=false APP_SECURITY_ALLOW_PLAINTEXT_GRPC=false APP_DEMO_SEED_ENABLED=false
export APP_LOG_DIR=/app/logs APP_RECORD_DIR=/app/records
exec /opt/java/openjdk/bin/java -jar /app/app.jar
