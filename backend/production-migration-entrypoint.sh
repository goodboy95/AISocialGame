#!/bin/sh
set -eu
umask 077
PATH=/opt/java/openjdk/bin:/usr/bin:/bin
JAVA_HOME=/opt/java/openjdk
export PATH JAVA_HOME

[ "$#" -eq 1 ] || { echo 'ai-social-game migration action is required' >&2; exit 64; }
case "$1" in checkpoint|precheck|execute|reconcile) ;; *) echo 'ai-social-game migration action is invalid' >&2; exit 64;; esac
migration_action=$1
[ -r /app/bin/production-load-env-file.sh ] || { echo 'ai-social-game migration env loader is unavailable' >&2; exit 1; }
set -- /app/env.txt
. /app/bin/production-load-env-file.sh
[ "${ENV:-}" = production ] || { echo 'ai-social-game production ENV is required' >&2; exit 1; }
[ -n "${SPRING_DATASOURCE_USERNAME:-}" ] || { echo 'ai-social-game database principal is unavailable' >&2; exit 1; }
[ -n "${SPRING_DATASOURCE_PASSWORD:-}" ] || { echo 'ai-social-game database credential is unavailable' >&2; exit 1; }

unset JAVA_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS MAVEN_OPTS MAVEN_ARGS \
  CLASSPATH BASH_ENV CDPATH LD_PRELOAD LD_LIBRARY_PATH LD_AUDIT \
  HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY SSL_CERT_FILE SSL_CERT_DIR HOSTALIASES
export APP_MYSQL_HOST=base.seekerhut.com APP_MYSQL_PORT=13306 APP_MYSQL_DATABASE=aisocialgame
export APP_MYSQL_USERNAME="$SPRING_DATASOURCE_USERNAME" APP_MYSQL_PASSWORD="$SPRING_DATASOURCE_PASSWORD"
export APP_MYSQL_PARAMS='useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=false'
export AIENIE_SOCIAL_MIGRATION_LEDGER=/app/release/migrations/sql-ledger.json
export AIENIE_SOCIAL_MIGRATION_PLAN=/app/release/migrations/production-plan.json

exec /opt/java/openjdk/bin/java \
  -Dloader.main=com.aisocialgame.migration.ProductionSocialMigrationMain \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher "$migration_action"
