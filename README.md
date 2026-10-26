# OCI Companion

OCI 프로파일 기반 리소스·소스 관리. Java 21 · Spring Foundation · OCI Java SDK.

## 실행

JDK 21+, GitHub CLI 필요. Maven Wrapper 포함.

```sh
./scripts/build.sh
./scripts/run.sh --spring.profiles.active=demo
```

<http://127.0.0.1:8091> → DEMO → 리전·구획 선택 → 새로 고침 → 소스 등록.
실제 OCI 연결은 `demo` 옵션 없이 실행합니다.

```sh
./scripts/run.sh
# 다른 설정 경로/포트
OCI_CONFIG_FILE=/absolute/path/config PORT=8092 ./scripts/run.sh
```

## 구현 범위

- `~/.oci/config` API 키 프로파일 선택. GetTenancy로 연결 확인.
- 구독 리전·접근 가능한 구획 조회. 선택 범위의 DevOps 프로젝트·저장소·빌드/배포 파이프라인 목록.
- JSON 캐시. 수동 갱신 또는 화면이 열려 있는 동안 1/5/15분 갱신.
- OCI 저장소 선택, GitHub/GitLab HTTPS 주소 등록·해제.
- 화면별 콘솔 경로, 현재 범위의 CLI, 권한·오류 안내.
- 루프백 바인딩, CSRF, 세션 분리, 이전 화면의 범위 변경 감지.

소스 등록은 로컬 참조 저장입니다. 외부 Git 인증·연결 검증·브랜치 조회는 후속 범위입니다.
실제 OCI 조회는 아직 검증하지 않았습니다. 데모는 OCI를 호출하지 않습니다.

## 저장

기본 경로: `.data/catalog`. 변경: `COMPANION_DATA_DIR`.

- 캐시: 프로파일·테넌시·사용자·리전·구획별 JSON.
- 소스: 캐시와 분리한 JSON. 갱신 시 유지.
- 임시 파일 작성 후 원자적 교체. 디렉터리 0700, 파일 0600. macOS/Linux 지원.
- 조회 실패 시 이전 캐시·조회 시각 유지. 손상된 소스 파일은 덮어쓰지 않음.
- 프로파일 원문·개인 키·토큰은 저장하지 않음.

현재 단일 로컬 프로세스용입니다. 여러 앱 인스턴스에서 같은 저장 경로를 공유하지 않습니다.
캐시는 현재 권한을 보장하지 않습니다. OCI 요청 권한은 API가 판단합니다.

## 기존 구현

`baseline-2026-10-26` 태그로 보존했습니다. 기존 SQLite 파일은 변경하지 않습니다.
기존 프로젝트·파이프라인 실행 화면은 명시적 `legacy` 모드에서 실행합니다.

```sh
./scripts/run.sh --spring.profiles.active=demo,legacy
```

기본 모드는 DB를 생성·연결하지 않습니다. 기존 SQLite 의존성은 legacy 실행용으로만 남아 있습니다.

## 개발

- [설계](docs/design/resource-catalog.md)
- [검증](docs/validation.md)
- [Redmine #977](https://redmine.cloud-handson.com/issues/977)
- [GitHub](https://github.com/devmrko/oci-companion)

Spring Foundation 소스 커밋: `abc0bb10a7ddf1d0767a044ebe654b58eea48ac3`.
빌드 스크립트가 `.tools`에 내려받아 프로젝트 전용 Maven 저장소에서 빌드합니다.
