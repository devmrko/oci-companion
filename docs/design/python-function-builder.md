# Functions에서 Python 소스를 빌드하기

사용자가 선택한 Functions 실행 경로를 유지하면서 Python 테스트·wheel 생성 결과를 Companion에서 확인하기 위한 설계다. 2026-11-08 현재 Maven Function 실행기는 실측됐지만 Python 실행기는 개발 중이다. 관리형 DevOps 러너 용량 조사는 별도 실험이며 기본 실행 경로 변경의 근거로 사용하지 않는다.

## 계약

별도 `python-v1` 이미지로 실행한다. 기존 maven-v1을 설정만 바꿔 Python으로 표시하지 않는다. 공개 Git URL과 40자리 커밋, 상대 작업 경로, test/package/verify 목적, 상대 산출물 패턴을 받는다. Python에서는 Maven profiles를 받지 않는다. 소스의 `requirements-build.lock`에는 테스트·런타임·빌드 백엔드 의존성을 모두 정확히 고정한다. 빌더의 venv 내부에서만 의존성을 설치하고 pip check 및 프로젝트 설치를 수행한다. test는 pytest, package/verify는 pytest 통과 후 wheel을 생성한다. 임의 shell 명령을 요청 값으로 받지 않는다.

용량은 호출 전에 probe로 확인하고 요청의 `requiredFreeBytes`에 소스 다운로드·압축 해제·의존성 설치·테스트·wheel·로그의 최대 동시 공간과 여유분을 포함한다. Function 환경 `BUILD_MIN_FREE_BYTES`는 관리자가 정한 하한이다. 두 값 중 큰 값에 못 미치면 Git 다운로드 전에 실패 결과를 저장한다. 부족하면 관리형 러너로 자동 전환하지 않는다. 메모리·실행 제한은 별도로 확인하며 다른 Maven 예제에서 본 /tmp 768MiB를 이 프로젝트에도 충분하다고 가정하지 않는다.

빌드 실행 UUID와 조건부 최초 result.json 생성, 주기적 로그 갱신, artifacts의 크기·SHA256, test-reports.tar.gz 규격은 유지한다. HTTP202는 완료가 아니다. 최종 SUCCEEDED와 wheel의 다운로드 검증으로 성공을 판단한다.

## 남은 연결과 완료 기준

1. 구현 완료: 별도 Python Function handler 및 로컬 연결 테스트.
2. 로컬 검증 완료, 실클라우드 미검증: private 프로젝트용 Object Storage 소스 번들: 허용 버킷·객체 버전·SHA256 검증, 안전한 압축 해제, 최소 읽기 정책. 정적 Git 토큰을 전달하지 않는다. 현재 공개 Git 입력만으로 Launchpad private 소스를 빌드했다고 주장하지 않는다.
3. 별도 Python 빌더 이미지 준비·Function 배포·probe. 빌더 이미지 최초 제작과 Function 내부 사용자 소스 빌드는 서로 다른 단계다.
4. 실행 화면·계약 조회 연결 완료. Python 템플릿 ZIP 생성은 남아 있으며 현재 생성 화면은 Maven 전용이다. python-v1은 별도 Python handler 이미지와 필수 설정을 먼저 배포해야 한다. 계약 이름만 바꾸는 것은 설치가 아니다.
5. Functions에서 실제 Python 테스트와 wheel 생성, Object Storage 결과 확인 및 Companion UI 검증.

컨테이너 이미지 생성은 별도 검증 항목이다. Functions 내 Docker daemon이 존재한다고 가정하거나 `python-v1` wheel 성공을 이미지 성공으로 표시하지 않는다. 이번 구현은 특정 앱·ADB·Langfuse에 종속되지 않는다.

## 2026-11-08 구현 확인

`src/main/resources/fn-python-template`에 독립 handler, Python 명령 구성, Dockerfile을 추가했다. 관리자가 `BUILD_MIN_FREE_BYTES`를 명시하고 요청도 `requiredFreeBytes`를 제공해야 빌드를 진행한다. `requirements-build.lock`의 중복·비고정 입력을 거부한다. pytest 실패 후 wheel을 만들지 않으며 JUnit XML을 기존 보고서 archive 형식으로 보관한다. 기존 Maven handler와 FunctionBuilds의 compatible 판정은 변경하지 않았다.

`python3 -m unittest discover -s tests -p test_python_function.py -v`의 4개 테스트가 통과했다. 명령 구성과 handler의 실패 기록 경로를 검증했고, OCI 저장소와 명령 실행은 테스트 대역이다. Functions의 실제 Python 빌드·wheel 다운로드 검증은 아직 하지 않았다. Dockerfile의 BASE_IMAGE는 공식 OCI Functions Python 3.12 이미지의 검증된 digest를 외부에서 제공해야 한다. 고정된 입력 없이 임의 최신 태그로 배포하지 않는다.

이 초기 버전은 전체 peak 메모리·디스크 사용량을 보장하지 않는다. 소스와 모든 의존성을 /tmp에 설치하므로 Launchpad 전체 의존성이 Functions 공간에 맞는지 먼저 probe 및 산정해야 한다. 필요하면 빌더 이미지에 고정 공통 의존성을 미리 넣고 변경되는 프로젝트 소스만 실행 공간에 두는 구조를 검토한다. 실행 중 원격 패키지 설치, 테스트, PEP 517 backend 실행은 신뢰하는 소스에만 허용한다. 결과 버킷 권한을 가진 Function에서 임의의 제3자 코드를 안전하게 실행하는 샌드박스로 간주하지 않는다.

## 비공개 Object Storage 소스 입력 구현

`source_bundle.py`와 Python handler에 비공개 입력 경로를 연결했다. 요청에는 `sourceBundle`의 object/version/sha256만 넣으며 namespace·bucket·허용 prefix는 관리자 Function 설정에서 가져온다. Git URL과 번들을 동시에 지정할 수 없다. Resource Principal의 객체 읽기 권한도 해당 버킷과 필요 범위로 한정해야 한다. 이 구현이 IAM 정책을 생성하지는 않는다.

```json
{
  "action": "build",
  "runId": "<새 UUID>",
  "commit": "<40자리 소스 커밋>",
  "sourceBundle": {
    "object": "sources/<소스 번들>.tgz",
    "version": "<Object Storage 객체 버전 ID>",
    "sha256": "<번들 파일의 64자리 SHA-256>"
  },
  "goal": "verify",
  "directory": "project",
  "artifact": "dist/*.whl",
  "requiredFreeBytes": 536870912
}
```

requiredFreeBytes의 숫자는 형식 설명용이며 Launchpad의 필요 용량 실측값이 아니다. 실제 probe·산정 결과로 바꾼다. 버전 관리를 활성화한 소스 버킷과 실제 객체 version ID가 필요하다. 정적 Git 토큰이나 PAR URL은 입력으로 전달하지 않는다.

관리자 설정은 BUILD_SOURCE_NAMESPACE, BUILD_SOURCE_BUCKET, BUILD_SOURCE_PREFIX(끝 `/` 필수)다. 선택 제한은 BUILD_SOURCE_MAX_BYTES(기본 64MiB), BUILD_SOURCE_MAX_UNPACKED_BYTES(256MiB), BUILD_SOURCE_MAX_ENTRIES(10,000), BUILD_SOURCE_RESERVE_BYTES(16MiB)이며 모두 양수다. 이 값은 Function 저장 공간을 늘리지 않는다. 전체 빌드 예산은 별도의 BUILD_MIN_FREE_BYTES·requiredFreeBytes로 검사한다.

번들 최상위 `source.json`은 `{"schema_version":1,"commit":"<40자리 SHA>"}`를 포함해야 한다. commit은 출처 추적용이며 해시·manifest가 코드 신뢰를 증명하지 않는다. 소스 게시자를 신뢰해야 한다. 패키지와 테스트 코드를 함께 넣고 project/requirements-build.lock으로 전체 의존성을 고정한다. 기존 Launchpad의 다중 저장소 번들은 manifest 필드가 달라 그대로 호환되지 않으며 별도 입력 패키징이 필요하다.

SDK get_object에 version_id를 전달하고 다운로드 크기·SHA256을 검사한다. 압축 전체의 항목·비압축 크기·중복 이름·절대/상위 경로·링크·특수 파일·sparse 항목을 확인한 뒤 새 디렉터리에 일반 파일만 쓴다. 전체 manifest를 검증하기 전에는 소스 디렉터리를 만들지 않는다. 압축 전송·해제 중 실패한 임시 파일은 invocation 작업 디렉터리 종료 때 정리한다.

2026-11-08 로컬 테스트는 총 8개 통과했다. 버전 인자 전달, 정상 추출, 잘못된 prefix/version/hash/commit, 경로 탈출·링크·중복 이름 및 압축 크기 초과 거부를 검증했다. 실제 Object Storage 다운로드, IAM 권한, Function 배포는 아직 검증하지 않았다. 후속 Java 연결에서 python-v1 호환 판정과 실행 화면을 추가했다. 기존 Maven 요청 경로는 유지한다.

## Companion에서 실행하기

Python 빌더를 배포한 운영자는 `/builds/functions`에서 Application과 Function을 선택하고 **빌드 실행**으로 이동한다. python-v1이면 비공개 소스 번들 전용 화면이 열린다. 공개 Git 입력은 Python handler에서 지원하지만 현재 Python 화면에는 노출하지 않는다.

| 입력 | 필수 여부 | 의미 |
|---|---|---|
| 소스 객체 이름 | 필수 | Function에 설정한 허용 버킷·prefix 안의 객체 |
| 객체 버전 ID | 필수 | 같은 이름의 객체가 바뀌어도 지정 버전 사용 |
| 번들 SHA-256 / 커밋 SHA | 필수 | 다운로드 바이트 검증 / source.json 출처 대조 |
| 필요 여유 공간(bytes) | 필수 | 실제 소스·의존성·테스트·산출물·예비 공간을 합한 양의 정수 예산 |
| Goal / 작업 경로 | 필수 | test 또는 테스트 후 wheel 생성 / 번들 내부 프로젝트 경로 |
| 산출물 패턴 | 선택 | 예: dist/*.whl. 생략해도 결과와 로그는 저장 |

Function 유형은 서버 세션의 일회용 실행 티켓에 보관한다. Python에 Git URL·Maven 프로파일을 함께 보내거나 Maven에 번들 입력을 보내면 호출 전에 거부한다. 호출 시 기존 어댑터가 현재 리전·compartment·Application·ACTIVE 상태·설정 etag를 다시 확인한다. 소스 버킷·prefix와 실제 공간 검사는 Function 안에서 수행하므로 폼 제출 성공만으로 해당 검사를 통과했다고 볼 수 없다.

접수 후 같은 실행 이력에서 결과 JSON, 로그, JUnit 보고서를 확인한다. Python의 package와 verify는 현재 동일하게 테스트 후 wheel을 생성한다. 저장된 빌드 작업 등록 화면은 아직 Maven 전용이며 Python Function 등록을 거부한다. Python 템플릿 다운로드와 저장 작업 확장은 후속 작업이다.

2026-11-08 Java 전체 `mvn verify` 통과. 새 MockMvc 테스트는 Python 폼 렌더링, 객체 버전·해시·정수 예산의 호출 전달, CSRF·티켓 재사용·교차 유형 입력 거부와 입력 검증을 확인한다. 실제 브라우저·OCI Function 호출을 대체하는 검증은 아니다. 실제 Python Function 배포와 Launchpad 빌드 성공은 아직 남아 있다.

## 비공개 Python 라이브러리 소스 빌드 설계

Launchpad처럼 공개 인덱스에 없는 내부 라이브러리를 사용하는 프로젝트는 동일 소스 번들에 라이브러리를 포함한다. 앱 작업 폴더의 선택 파일 `build-local.json`에는 schema_version=1과 packages 배열을 둔다. 각 항목은 path(앱 내부 상대 폴더), name, version만 받는다. 최대 8개이며 나열 순서대로 설치한다. pyproject.toml의 정적 이름·버전을 대조하고 잠금 파일과 중복되는 패키지, 링크·외부 경로를 거부한다. 별도의 shell 명령이나 저장소 인증값은 받지 않는다.

전체 공개 의존성과 빌드 백엔드는 requirements-build.lock으로 먼저 설치하고, 내부 소스 → 앱 설치 → pip check → 앱 테스트 → 내부 라이브러리와 앱 wheel 생성 순으로 진행한다. 모든 빌드는 Function 안에서 수행한다. 라이브러리 자체 테스트 전체를 수행한다는 의미는 아니며 앱 통합 테스트 범위를 결과와 구분한다. 이 소스들과 추가 빌드 공간을 용량 예산에 포함해야 한다.

## 임시 공간이 작은 Function의 의존성 이미지

공개 의존성을 매번 가상환경에 설치할 공간이 부족하면 `Dockerfile.dependencies`로 별도 python-v1 러너 이미지를 확장한다. RUNNER_IMAGE에는 먼저 만든 러너의 digest를 지정하고 소스와 동일한 requirements-build.lock을 이미지에 포함한다. 이미지 생성 단계에서 pip install과 pip check를 통과해야 한다. 현재 이 Dockerfile은 아직 실제 빌드하지 않았다.

BUILD_PREINSTALLED_LOCK이 설정된 경우 소스 잠금 파일과 이미지 잠금 파일의 전체 항목, 실제 설치된 모든 버전을 확인한 후에만 system-site-packages 가상환경을 만든다. 버전이 다르면 즉시 실패하며 다시 다운로드하는 우회는 하지 않는다. 공개 의존성은 읽기 전용 이미지 레이어에서 사용하고 내부 라이브러리·앱은 실행별 가상환경에 설치한다. 설정이 없으면 기존 격리 가상환경 경로를 유지한다. 이미지 의존성도 Function 메모리 소비를 줄여 주지는 않으며 실제 probe·최대 사용량 검증이 필요하다.

2026-11-08 로컬 Python 테스트 11개 통과(Python 3.14). 대상 Python 3.12 Linux 실실행과 이미지 제작·배포는 미검증이다. macOS 기본 /usr/bin/python3(3.9)는 tomllib이 없어 테스트 실행기로 사용할 수 없다.

## 실제 FDK 시작 경로 검증

2026-11-08 새 공식 베이스 이미지로 만든 컨테이너는 import 검사와 pip check를 통과했지만 실제 Function probe에서 502 FunctionInvokeContainerInitFail을 반환했다. 호출 로그의 원인은 `exec /usr/local/bin/fdk failed: No such file or directory`였다. 기존 베이스에서 사용한 절대 경로를 새 이미지에도 그대로 적용한 것이 잘못이었다.

Python Dockerfile은 `/usr/bin/env fdk`로 이미지 PATH의 실행 파일을 찾도록 수정했다. 앞으로 이미지 검사에서는 entrypoint를 python으로 덮어쓰는 import 검사에 더해 실제 기본 entrypoint를 실행하고 FDK 소켓을 통해 probe 응답까지 검증한다. 소스 수정이나 이미지 빌드 성공만으로 실클라우드 오류가 해결됐다고 표시하지 않는다.
