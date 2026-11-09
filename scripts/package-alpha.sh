#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=0.1.0-alpha.1
jar_path=target/oci-companion-0.1.0-SNAPSHOT.jar
[[ -f "$jar_path" ]] || { echo 'Run ./scripts/build.sh first.' >&2; exit 1; }
mkdir -p .tools/releases
staging=$(mktemp -d "$PWD/.tools/releases/package.XXXXXX")
trap 'rm -rf "$staging"' EXIT
package="$staging/oci-companion-$version"
mkdir -p "$package"
cp "$jar_path" "$package/oci-companion.jar"
cat > "$package/run.sh" <<'RUN'
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
java_info=$("$java_bin" -XshowSettings:properties -version 2>&1) || { echo 'JDK 21 이상을 설치하고 JAVA_HOME 또는 PATH를 설정하세요.' >&2; exit 1; }
java_major=$(printf '%s\n' "$java_info" | awk '/java.specification.version =/ {print $3}')
[[ "$java_major" =~ ^[0-9]+$ && "$java_major" -ge 21 ]] || { echo 'JDK 21 이상이 필요합니다.' >&2; exit 1; }
umask 077
exec "$java_bin" -jar oci-companion.jar "$@"
RUN
chmod +x "$package/run.sh"
cp docs/releases/alpha-readiness.md "$package/VALIDATION.md"
cat > "$package/README.txt" <<'GUIDE'
OCI Companion 0.1.0-alpha.1 — Public Alpha

1. JDK 21 이상을 설치합니다.
2. ~/.oci/config에 OCI API 키 프로파일을 준비합니다.
3. 터미널에서 ./run.sh 를 실행합니다.
4. http://127.0.0.1:8091 에서 프로파일·리전·구획을 선택합니다.

다른 OCI 설정 파일: OCI_CONFIG_FILE=/absolute/path/config ./run.sh
다른 포트: ./run.sh --server.port=8092
설정·소스 참조·캐시는 이 폴더의 .data/catalog에 저장됩니다.
업데이트 시 기존 .data를 보존하세요. 개인 설정을 배포본에 넣지 마세요.
공유 서버·외부 네트워크 공개는 이 알파의 지원 범위가 아닙니다.

VALIDATION.md를 먼저 확인하세요. 실제 OCI 생성·빌드·실행에는 비용이 발생합니다.
문제 신고: https://github.com/devmrko/oci-companion/issues
키·토큰·.env·프로파일 원문을 이슈에 첨부하지 마세요.
GUIDE
(cd "$staging" && zip -qr package.zip "oci-companion-$version")
mv "$staging/package.zip" ".tools/releases/oci-companion-$version.zip"
shasum -a 256 ".tools/releases/oci-companion-$version.zip" > ".tools/releases/oci-companion-$version.zip.sha256"
echo ".tools/releases/oci-companion-$version.zip"
