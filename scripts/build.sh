#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools .data
export MAVEN_USER_HOME="$PWD/.tools/maven-user"
./mvnw -B -Dmaven.repo.local="$PWD/.tools/m2" verify
