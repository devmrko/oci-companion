# OCI Companion

OCI 프로파일 기반 리소스·소스 관리. Java 21 · Spring Boot · OCI Java SDK.

## Public Alpha

로컬 실행용 초기 버전입니다. [릴리스](https://github.com/devmrko/oci-companion/releases)에서 실행 ZIP을 내려받거나 소스로 빌드할 수 있습니다. [지원 범위·알려진 제한](docs/releases/alpha-readiness.md)을 먼저 확인하세요.

## 실행

JDK 21 이상이 필요합니다. ZIP을 풀고 `./run.sh`를 실행하세요(macOS/Linux). GitHub CLI·별도 DB는 필요하지 않습니다.

소스 빌드에는 Git과 Maven 의존성 다운로드를 위한 인터넷 연결이 필요합니다. Maven Wrapper를 포함합니다.

```sh
./scripts/build.sh
./scripts/run.sh
```

<http://127.0.0.1:8091> → OCI 프로파일 선택 → 리전·구획 선택 → 새로 고침.

개인 Tailscale 접속에는 `COMPANION_EXTRA_HOSTS=<이 서버의 Tailscale IPv4>`를 설정하고 앱을 시작한 뒤 `tailscale serve --bg --tcp=8091 tcp://127.0.0.1:8091`로 연결할 수 있습니다. 접속 주소는 `http://<Tailscale IPv4>:8091`입니다. 기존 loopback 접속은 유지하며 추가 호스트는 정확히 일치할 때만 허용합니다. 접속자는 서버의 OCI 프로파일을 사용할 수 있으므로 Tailscale 접근 권한을 신뢰한 사용자로 제한하세요. 인터넷 공개·다중 사용자 인증 기능은 아닙니다. 전달 해제는 `tailscale serve --tcp=8091 off`입니다.
배포 JAR에는 샘플 응답 어댑터를 포함하지 않습니다.

```sh
./scripts/run.sh
# 다른 설정 경로/포트
OCI_CONFIG_FILE=/absolute/path/config PORT=8092 ./scripts/run.sh
```

## 구현 범위

- `~/.oci/config` API 키 프로파일 선택. GetTenancy로 연결 확인.
- 구독 리전·접근 가능한 구획 선택. 선택 구획 / 하위 포함 / 전체 구획 조회.
- OCI 목록의 프로젝트·이름·상태 필터. 구획별 성공 시각·소요 시간·오류 표시.
- JSON 캐시. 선택 구획은 수동 또는 열린 화면에서 1/5/15분 갱신.
- 하위·전체 구획은 명시적 수동 조회. 브라우저 동시 조회 최대 4개, 진행 표시.
- OCI 저장소는 선택 리전·구획 범위에서 등록. GitHub/GitLab 등록은 프로파일 공통.
- 화면별 콘솔 경로, 현재 범위의 CLI, 권한·오류 안내.
- 블록 볼륨 백업·복원, OCI 정기 정책 조회·연결·교체·해제.
- 루프백 바인딩, CSRF, 세션 분리, 이전 화면의 범위 변경 감지.

소스 등록 자체는 로컬 참조 저장입니다. Git 인증과 OCI 외부 연결은 별도 설정입니다.
DEFAULT 프로파일의 실제 인증·리전·구획·DevOps 목록 조회를 검증했습니다.
검증 범위: us-ashburn-1 접근 가능 구획 307개. 프로젝트 3개·저장소 5개 조회.
빌드는 [6개 언어 x86 컨테이너 예제](examples/rest-hello/README.md)와 Registry 전달·로그·보고서를 실제 검증했습니다. 배포 대상별 검증은 별도입니다.

## 메뉴

DB Companion과 같은 좌측 그룹 메뉴·접기/펼치기·상단 연결 정보 구조입니다.
워크스페이스 / 빌드·배포 / 인프라·운영 / 관리로 구분합니다.
미구현 메뉴는 `예정`으로 표시하며 실행되지 않습니다.

## 저장

기본 경로: `.data/catalog`. 변경: `COMPANION_DATA_DIR`.

- 캐시: 프로파일·테넌시·사용자·리전·구획별 JSON.
- 소스: 캐시와 분리한 JSON. 갱신 시 유지.
- 기존 외부 Git 등록은 같은 프로파일·테넌시·사용자의 공통 파일로 자동 이전. 손상 파일은 보존.
- 임시 파일 작성 후 원자적 교체. 디렉터리 0700, 파일 0600. macOS/Linux 지원.
- 조회 실패 시 이전 캐시·조회 시각 유지. 손상된 소스 파일은 덮어쓰지 않음.
- 프로파일 원문·개인 키·토큰은 저장하지 않음.

현재 단일 로컬 프로세스용입니다. 여러 앱 인스턴스에서 같은 저장 경로를 공유하지 않습니다.
캐시는 현재 권한을 보장하지 않습니다. OCI 요청 권한은 API가 판단합니다.

## 기존 구현

`baseline-2026-10-26` 태그로 보존했습니다. 기존 SQLite 파일은 변경하지 않습니다.
기존 프로젝트·파이프라인 실행 화면은 명시적 `legacy` 모드에서 실행합니다.

```sh
./scripts/run.sh --spring.profiles.active=legacy
```

기본 모드는 DB를 생성·연결하지 않습니다. 기존 SQLite 의존성은 legacy 실행용으로만 남아 있습니다.

## 개발

- [설계](docs/design/resource-catalog.md)
- [CI/CD 품질 기준·완료 조건](docs/design/cicd-quality-criteria.md)
- [재사용 빌더 이미지](docs/design/reusable-builders.md)
- [트리거·예약 실행과 검증 범위](docs/design/build-automation.md)
- [검증](docs/validation.md)
- [GitHub](https://github.com/devmrko/oci-companion)

비공개 저장소 의존성 없이 공개 Maven 의존성으로 빌드합니다. `./scripts/package-alpha.sh`는 실행 ZIP과 SHA-256 파일을 생성합니다.

명세 저장·런타임·테스트 보고서: [사용 흐름과 Git 인증 설정](docs/design/build-specification-delivery.md).

성공한 빌드의 파일을 확인하고 준비된 VM 파이프라인으로 배포하려면 [빌드에서 배포로 연결하기](docs/design/build-deployment-handoff.md)를 참고하세요.
