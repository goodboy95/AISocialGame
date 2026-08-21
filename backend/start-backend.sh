#!/bin/sh
set -eu

JAVA_HOME=/opt/java/openjdk
PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export JAVA_HOME PATH

[ -r /app/bin/runtime-process-environment.sh ] || {
  echo "Canonical runtime process environment policy is unavailable" >&2
  exit 1
}
# shellcheck source=/dev/null
. /app/bin/runtime-process-environment.sh

clear_process_overrides

if [ "$#" -gt 0 ]; then
  [ -r /app/bin/staging-load-env-file.sh ] || {
    echo "Canonical runtime env loader is unavailable" >&2
    exit 1
  }
  # shellcheck source=/dev/null
  . /app/bin/staging-load-env-file.sh "$@"
fi

JAVA_HOME=/opt/java/openjdk
PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export JAVA_HOME PATH
clear_process_overrides
[ "${ENV:-}" = test ] || {
  echo 'AISocialGame reviewed launcher is staging-only; production remains frozen' >&2
  exit 1
}
export SERVER_ADDRESS=0.0.0.0 SERVER_PORT=20030
export QDRANT_HOST=http://base.testhut.top QDRANT_PORT=16333
export GRPC_CLIENT_USER_SECURITY_TRUST_CERT_COLLECTION=file:/run/aienie/trust/staging-root.pem
export GRPC_CLIENT_BILLING_SECURITY_TRUST_CERT_COLLECTION=file:/run/aienie/trust/staging-root.pem
export GRPC_CLIENT_AI_SECURITY_TRUST_CERT_COLLECTION=file:/run/aienie/trust/staging-root.pem
exec /opt/java/openjdk/bin/java -jar /app/app.jar
