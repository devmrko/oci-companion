#!/usr/bin/env bash
set -euo pipefail
ROOT="$(pwd)"
if ! command -v lsof >/dev/null 2>&1; then
  dnf install -y lsof
fi
curl --fail --location --retry 3 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz' -o /tmp/db-companion-jdk.tar.gz
echo 'd7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c  /tmp/db-companion-jdk.tar.gz' | sha256sum -c -
mkdir -p "$ROOT/.jdk"
tar xzf /tmp/db-companion-jdk.tar.gz --strip-components=1 -C "$ROOT/.jdk"
export JAVA_HOME="$ROOT/.jdk"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn --version
node --version
node -e 'if (Number(process.versions.node.split(".")[0]) < 22) process.exit(1)'
git init source
git -C source remote add origin https://github.com/devmrko/db-companion.git
git -C source fetch --depth 1 origin 7a5cb34b68d8dae90f9c398667a0db976bb2f6d2
git -C source checkout --detach FETCH_HEAD
test "$(git -C source rev-parse HEAD)" = 7a5cb34b68d8dae90f9c398667a0db976bb2f6d2
cd source
mvn -B -ntp -Pquality clean verify
node --test src/test/js/*.test.mjs
node scripts/publication-check.cjs
mkdir -p "$ROOT/output"
cp target/db-manage-companion-*.jar "$ROOT/output/db-companion.jar"
printf '%s\n' 'https://github.com/devmrko/db-companion' '7a5cb34b68d8dae90f9c398667a0db976bb2f6d2' > "$ROOT/output/source.txt"
tar czf "$ROOT/output/test-reports.tar.gz" target/surefire-reports target/pmd.xml
cd "$ROOT/output"
sha256sum db-companion.jar test-reports.tar.gz source.txt > SHA256SUMS
cat SHA256SUMS
