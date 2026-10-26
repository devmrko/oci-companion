#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p .data
if [[ ! -f target/oci-companion-0.1.0-SNAPSHOT.jar ]]; then
  echo 'Run ./scripts/build.sh first.' >&2
  exit 1
fi
exec java -jar target/oci-companion-0.1.0-SNAPSHOT.jar "$@"
