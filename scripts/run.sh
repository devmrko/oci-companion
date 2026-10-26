#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p .data
if [[ ! -f target/oci-companion-0.1.0-SNAPSHOT.jar ]]; then
  echo 'Run ./scripts/build.sh first.' >&2
  exit 1
fi
mkdir -p .tools/runtime
runtime_dir=$(mktemp -d "$PWD/.tools/runtime/run.XXXXXX")
cp target/oci-companion-0.1.0-SNAPSHOT.jar "$runtime_dir/app.jar"
exec java -jar "$runtime_dir/app.jar" "$@"
