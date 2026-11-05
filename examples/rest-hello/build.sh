#!/usr/bin/env bash
set -euo pipefail
lang=${1:?language required}
case "$lang" in python|javascript|typescript|go|ruby|java) ;; *) exit 2;; esac
cd "$(dirname "$0")"
test "$(uname -m)" = x86_64 || { echo 'This example run requires x86'; exit 1; }
image="hello-rest:x86-v1"
name="hello-${lang}-test"
probe="${name}-probe"
cleanup() {
  docker logs "$name" 2>&1 || true
  docker rm -f "$probe" "$name" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker build --tag "$image" "$lang"
test "$(docker image inspect "$image" --format '{{.Architecture}}')" = amd64
docker run -d --name "$name" "$image"
# Docker may be a remote daemon: test on the application's network, not the build host.
result=0
docker run -i --name "$probe" --network "container:$name" python:3.11-slim python - http://127.0.0.1:8080 "$lang" < verify.py || result=$?
mkdir -p reports
docker cp "$probe:/reports/." reports/
exit "$result"
