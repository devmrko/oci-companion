#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
foundation_revision=abc0bb10a7ddf1d0767a044ebe654b58eea48ac3
mkdir -p .tools .data
export MAVEN_USER_HOME="$PWD/.tools/maven-user"
if [[ ! -f .tools/foundation-installed ]] || [[ "$(cat .tools/foundation-installed)" != "$foundation_revision" ]]; then
  source_dir=".tools/foundation-$foundation_revision"
  if [[ ! -f "$source_dir/pom.xml" ]]; then
    mkdir -p "$source_dir"
    gh api "repos/devmrko/spring-foundation/tarball/$foundation_revision" > .tools/foundation-source.tar.gz
    tar -xzf .tools/foundation-source.tar.gz -C "$source_dir" --strip-components=1
  fi
  ./mvnw -B -f "$source_dir/pom.xml" -Dmaven.repo.local="$PWD/.tools/m2" -pl foundation-starter -am install -DskipTests -Dmaven.javadoc.skip=true
  printf '%s\n' "$foundation_revision" > .tools/foundation-installed
fi
./mvnw -B -Dmaven.repo.local="$PWD/.tools/m2" verify
