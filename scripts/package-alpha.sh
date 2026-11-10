#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version="${1:-0.1.0-alpha.10}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+-alpha\.[0-9]+$ ]] || { echo 'Expected version: x.y.z-alpha.n' >&2; exit 1; }
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
mkdir -p "$package/validation"
cp docs/validation/*.md "$package/validation/"
sed 's#](../validation/#](validation/#g' docs/releases/alpha-readiness.md > "$package/VALIDATION.md"
cat > "$package/README.txt" <<GUIDE
OCI Companion $version — Public Alpha

1. JDK 21 이상을 설치합니다.
2. ~/.oci/config에 OCI API 키 프로파일을 준비합니다.
3. 터미널에서 ./run.sh 를 실행합니다.
4. http://127.0.0.1:8091 에서 프로파일·리전·구획을 선택합니다.

다른 OCI 설정 파일: OCI_CONFIG_FILE=/absolute/path/config ./run.sh
다른 포트: ./run.sh --server.port=8092
설정·소스 참조·캐시는 이 폴더의 .data/catalog에 저장됩니다.
업데이트는 기존 설치 폴더의 JAR를 교체하세요. .data와 .tools를 보존하세요.
OKE 연결에는 절대 경로가 저장됩니다. 폴더를 옮겼다면 OKE 연결을 다시 저장하세요.
개인 설정을 배포본에 넣지 마세요.
OKE 상태·로그 조회: 앱 서버에 OCI CLI와 kubectl을 설치하세요.
COMPANION_OCI_CLI, COMPANION_KUBECTL에 각 실행 파일의 절대 경로를 지정하세요.
예: COMPANION_KUBECTL=/opt/homebrew/bin/kubectl COMPANION_OCI_CLI=/opt/homebrew/bin/oci ./run.sh
OKE 조회 연결 화면에서 파일·실행 권한을 확인할 수 있습니다. OCI 인증·RBAC는 별도로 확인합니다.
공유 서버·외부 네트워크 공개는 이 알파의 지원 범위가 아닙니다.

VALIDATION.md를 먼저 확인하세요. 실제 OCI 생성·빌드·실행에는 비용이 발생합니다.
문제 신고: https://github.com/devmrko/oci-companion/issues
키·토큰·.env·프로파일 원문을 이슈에 첨부하지 마세요.
GUIDE
(cd "$staging" && zip -qr package.zip "oci-companion-$version")
mv "$staging/package.zip" ".tools/releases/oci-companion-$version.zip"
(cd .tools/releases && shasum -a 256 "oci-companion-$version.zip" > "oci-companion-$version.zip.sha256")
echo ".tools/releases/oci-companion-$version.zip"
