# OCI Companion

로컬에서 OCI 프로파일로 연결하고 프로젝트별 빌드·VM 배포를 관리하는 웹 앱입니다.
Java 21 · Spring Foundation · OCI Java SDK · SQLite. 현재 첫 수직 기능 구현 단계입니다.

## 실행

JDK 21+ 및 GitHub CLI(`gh`)가 필요합니다. Foundation 저장소가 비공개라면 접근 가능한
GitHub 계정으로 `gh` 인증이 필요합니다. Maven은 포함된 Wrapper를 사용합니다.

```sh
./scripts/build.sh
./scripts/run.sh --spring.profiles.active=demo
```

<http://127.0.0.1:8091> → DEMO 선택 → 프로젝트 등록. 데모 모드는 실제 OCI를 호출하지
않고 샘플 응답을 사용합니다. 실제 연결은 demo 옵션 없이 실행합니다.

```sh
./scripts/run.sh
# 선택: OCI_CONFIG_FILE=/absolute/path/config PORT=8092 ./scripts/run.sh
```

서버의 `~/.oci/config`에 있는 API 키 프로파일을 선택하고 OCI GetTenancy 호출로
연결을 확인합니다. 해당 조회 권한이 필요합니다. 연결 성공이 모든 기능의 권한을
보장하지는 않습니다. 개인 키·인증정보는 DB에 복사하지 않습니다. 프로파일 목록은
로컬 연결 화면에 노출되므로 공용 서버용 로그인 방식으로 사용하지 마세요.

## 현재 동작하는 범위

- 프로파일 목록, 연결/해제, 세션 및 CSRF 보호. 기본 loopback 바인딩.
- tenancy·사용자·리전별 프로젝트 설정 저장/편집, SQLite 재시작 후 유지.
- Maven/Gradle/Python/TypeScript 빌드 명세 초안 다운로드.
- 컴파트먼트의 기존 빌드·배포 파이프라인 조회.
- 기존 파이프라인 빌드 실행 및 VM rolling deployment 실행 요청.
- 요청 중복 방지, 실행 이력, OCI 실행 상태 수동 갱신.
- 소스·빌드·배포본·VM·IAM에 대한 콘솔 절차, CLI/SDK 대응, 공식 문서 안내.

빌드·배포는 **OCI에 이미 구성된 설정**으로 실행합니다. 로컬 Git URL/브랜치와
템플릿은 아직 자동 반영하지 않습니다. `build_spec.yaml`은 프로젝트에 맞게 수정 후
커밋해야 합니다. 빌드 실행에 연결된 자동 배포 트리거도 동작할 수 있습니다.
VM 직접 배포는 instance group rolling deployment + approval/wait 단계만 허용합니다.

자동 리소스 생성, 저장소 분석/접근 인증, 런타임 자동 설치, 산출물 선택/승격,
상세 로그 스트리밍, 취소·승인·롤백, Functions, OKE는 아직 구현하지 않았습니다.
운영 검증된 배포 도구라는 뜻이 아닙니다. 테스트에서는 실제 OCI 리소스를 변경하지 않습니다.

## 저장과 교체

기본 `.data/companion.db`에는 설정과 요청 이력만 저장합니다. 파이프라인·실행 결과의
원본은 OCI입니다. 원격 상태는 마지막 조회 시각과 함께 표시합니다. 앱을 종료해도 OCI
작업은 계속됩니다. UNKNOWN 또는 오래된 SUBMITTING은 OCI 콘솔에서 확인하고 새 실행
여부를 결정해야 합니다. 불명확한 요청은 자동으로 재전송하지 않습니다.

`WorkspaceRepository` / `RunRepository` 뒤에 JDBC 구현을 둡니다. 다른 DB로 바꿀 때
어댑터·드라이버·마이그레이션과 데이터 이전을 준비합니다. 환경 변수 하나만 바꾸면
모든 DB에서 작동한다는 의미는 아닙니다. Object Storage는 추후 내보내기/백업용으로
추가할 수 있으며 현재 구현하지 않았습니다. 실행 중인 DB 파일을 단순 업로드하거나
여러 PC에서 공유 마운트하지 마세요. 앱 종료 후 `.data` 디렉터리를 보관할 수 있습니다.

OCI 인증/실행과 빌드 레시피, 배포 대상, 안내 문서도 포트 인터페이스로 분리했습니다.
설계와 후속 범위는 [architecture.md](docs/design/architecture.md)를 참고하세요.

## 재현 가능한 의존성

Spring Foundation은 커밋 `abc0bb10a7ddf1d0767a044ebe654b58eea48ac3`의 소스를
`.tools`에 내려받아 프로젝트 전용 Maven 저장소에 빌드합니다. 업스트림 코드를 앱에
복사하지 않습니다. Foundation의 SNAPSHOT 이름은 같아도 위 커밋을 고정합니다.
실제 OCI 리소스 호출 검증은 별도이며 데모 성공과 구분합니다.
