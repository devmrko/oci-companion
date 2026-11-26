# Function에서 잠금 파일 변경을 처리하기

기존 빌더를 사람이 다시 만들지 않아도 의존성이 바뀐 Sustena 소스를 빌드하도록, Function이 의존성 레이어와 새 실행 이미지를 만들고 Companion이 실행 환경을 확인한 뒤 빌드를 이어갑니다. 운영 Function과 운영 앱에는 이 실험 기능을 적용하지 않았습니다.

## 검증 대상과 방법

2026-11-26 JOUNGMINKOKOR_API, 춘천의 기존 ARM 앱 안에 별도 `companion-verify-lock-builder` Function을 만들었습니다. 새 IAM·보안 규칙·Secret은 만들거나 고치지 않았고 기존 pull Secret의 인증을 메모리에서 재사용했습니다. VM은 사용하지 않았습니다.

Gitea `new-sustena`의 별도 브랜치 `companion-verify-dependency-cache`, [검증 커밋 66d3725](https://gittea.cloud-handson.com/joungmin/new-sustena/commit/66d3725192b0764fea7c05a2e47b389aef511e47)에서 개발 의존성 `pyfiglet==1.0.4`와 그 설치 버전을 확인하는 테스트를 추가했습니다. 실제 잠금 파일 해시는 `1af9053b502db92a77c01f4297d512ae001ac0bb8f1e47e754663e46b6398b55`입니다. Function의 `uv sync --locked`가 잠금 파일과 프로젝트 입력의 일치를 검사했습니다.

검증은 opt-in `FunctionDependencyCacheLiveTest`로 실제 Companion `FunctionImageBuilds.build`를 호출했습니다. 소스는 해당 Git 커밋의 archive이며 OCI 코드 저장소 다운로드 및 웹 화면 조작은 이 테스트 범위에 포함하지 않았습니다. 앱 이미지 출력은 별도 private OCIR 저장소입니다. 런타임·소스 캐시에는 기존 빌더 저장소의 새 태그만 사용하고 기존 태그는 변경하지 않았습니다.

## 발견한 오류와 수정

첫 준비 시도는 다른 OCIR 저장소로 큰 기반 이미지를 복사하는 로컬 `crane append`가 300초 제한을 초과했습니다. 오류 원문은 `subprocess.TimeoutExpired: Command '[crane, append, -b, <existing-runtime@digest>, -f, <code.tar>, -t, <verification-runtime-tag>]' timed out after 300 seconds`입니다. 새 Function 생성 전이었고 임시 저장소 두 개를 삭제했습니다. 이후 같은 저장소 안에 작은 코드 레이어만 추가해 준비를 마쳤습니다.

코드 검토에서 리전 키 기반 Function 이미지 주소에 `.ocir.io`가 빠지는 오류를 발견해 수정하고 회귀 테스트를 추가했습니다.

첫 실제 의존성 설치·런타임 조립은 성공했지만, 관리 API가 새 digest·ACTIVE를 반환한 직후 재호출은 다시 `RUNTIME_REQUIRED`를 반환했습니다. 첫 자동 흐름은 62.07초에 `빌드·배포본 일치 확인 불가. 파일은 불변 저장소·SHA-256·크기, 이미지는 실행 기록의 주소·digest와 배포 명세를 확인하세요.`로 실패 처리했습니다. 성공으로 덮어쓰지 않았습니다. 이후 별도 재호출은 146초와 143초에 성공하여 새 런타임에서 실행 가능한 것을 확인했습니다.

전파 확인을 보강했습니다. 관리 API 대기 후 읽기 전용 probe가 실제 실행 환경의 잠금 파일 SHA-256과 aarch64를 확인해야 앱 빌드를 이어갑니다. 최대 180초 동안 확인하지 못하거나 호출이 실패하면 앱 빌드를 재실행하지 않습니다. 이를 적용한 검증에서는 테스트용 의존성 캐시를 삭제한 상태부터 시작했습니다.

## 최종 실행 결과

| 실행 | 실제 결과 | 시간 |
|---|---|---|
| 캐시 없는 첫 빌드 | 의존성·런타임 이미지 생성 → 실제 잠금 해시 확인 → 테스트 156개, 실패·오류 0 → push | 전체 180초; 의존성 준비 28.43초; 앱 처리 138.25초 |
| 같은 커밋 재빌드 | `dependencyCacheHit=true`, 런타임 전환 요청 없이 테스트 156개, 실패·오류 0 → push | 전체 144초; 앱 처리 138.35초 |

첫 앱 digest는 `sha256:fde638cd0a80ec36c777c1a02ba7f0aa197f98a8dad20c645fc6367722eae834`, 재빌드 digest는 `sha256:9516e9b3ac5081493fc91e5c3db4a4b2f1a01290f2f9957f93f5cf6da770f03a`입니다. 새 Function 런타임 digest는 `sha256:24e0bff0f3a77f4838299a6548aa56b9aba7d551349d92296b6f124235036f45`였습니다. 실행 로그는 `/tmp/ridley-cache-live-final.log`, 결과는 `.tools/dependency-cache/attempt2/build-1.json`, `build-2.json`입니다. 화면 기록 `.tools/verification/screenshots/77-function-dependency-cache-evidence.png`는 실제 로그·JSON을 렌더링한 증거 보고서이며 Companion UI 스크린샷은 아닙니다.

전체 `./mvnw verify`는 **417개 중 415개 통과, 실제 OCI opt-in 2개 기본 skip**, Python 테스트 **10개 통과**입니다. 위 실제 Function opt-in 테스트는 별도로 실행해 통과했습니다.

## 정리와 운영 보존

검증 Function과 새 출력 저장소를 삭제했고, 기존 빌더 저장소에는 이번 검증이 만든 이미지 14개만 삭제했습니다. 기존 운영 Function의 digest와 기존 저장소를 유지한 것을 확인했습니다. 정리 결과는 `/tmp/ridley-cache-clean2.log`, 비공개 리소스 참조는 `.tools/dependency-cache/attempt2/resources.json`에 있습니다.

운영 `sustena-c`를 읽기 전용으로 다시 확인해 두 Pod 모두 `d52337647c0c…`, 2/2 Ready, readyz HTTP 200, 이미지 외 Deployment 설정·Service·ConfigMap 동일을 확인했습니다. `.tools/verification/production-final-readonly-acceptance.json`에 결과가 있습니다. 이번 의존성 검증에서는 운영 배포나 운영 빌더를 변경하지 않았습니다.

## 적용 범위

Sustena용 Python 3.13 ARM64 계약과 digest로 고정한 기반 이미지를 대상으로 검증했습니다. 최초 Function 실행 도구·코드를 준비하는 단계는 별도로 필요합니다. 이후 잠금 파일 변경의 의존성 설치와 런타임 레이어 생성은 Function 안에서 수행합니다. 운영 빌더의 자동 의존성 옵션은 켜지 않았습니다.

초기 승인 도구의 `guardian review completed without an assessment payload` 및 승인 검토 deadline 오류 때문에 실행되지 않은 로컬 uv 설치·프로세스 명령이 있었습니다. 작은 명령으로 재시도 후 클라우드 검증은 진행했으며 로컬 uv 설치는 필요하지 않았습니다. 전체 오류와 실행 증거는 `.tools/dependency-cache/` 및 `/tmp/ridley-cache-*.log`에 있고 비밀값·전체 OCID는 본문에 포함하지 않습니다.
