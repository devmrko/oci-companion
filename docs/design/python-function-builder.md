# Functions에서 Python 소스를 빌드하기

사용자가 선택한 Functions 실행 경로를 유지하면서 Python 테스트·wheel 생성 결과를 Companion에서 확인하기 위한 설계다. 2026-11-08 현재 Maven Function 실행기는 실측됐지만 Python 실행기는 개발 중이다. 관리형 DevOps 러너 용량 조사는 별도 실험이며 기본 실행 경로 변경의 근거로 사용하지 않는다.

## 계약

별도 `python-v1` 이미지로 실행한다. 기존 maven-v1을 설정만 바꿔 Python으로 표시하지 않는다. 공개 Git URL과 40자리 커밋, 상대 작업 경로, test/package/verify 목적, 상대 산출물 패턴을 받는다. Python에서는 Maven profiles를 받지 않는다. 소스의 `requirements-build.lock`에는 테스트·런타임·빌드 백엔드 의존성을 모두 정확히 고정한다. 빌더의 venv 내부에서만 의존성을 설치하고 pip check 및 프로젝트 설치를 수행한다. test는 pytest, package/verify는 pytest 통과 후 wheel을 생성한다. 임의 shell 명령을 요청 값으로 받지 않는다.

용량은 호출 전에 probe로 확인하고 요청의 `requiredFreeBytes`에 소스 다운로드·압축 해제·의존성 설치·테스트·wheel·로그의 최대 동시 공간과 여유분을 포함한다. Function 환경 `BUILD_MIN_FREE_BYTES`는 관리자가 정한 하한이다. 두 값 중 큰 값에 못 미치면 Git 다운로드 전에 실패 결과를 저장한다. 부족하면 관리형 러너로 자동 전환하지 않는다. 메모리·실행 제한은 별도로 확인하며 다른 Maven 예제에서 본 /tmp 768MiB를 이 프로젝트에도 충분하다고 가정하지 않는다.

빌드 실행 UUID와 조건부 최초 result.json 생성, 주기적 로그 갱신, artifacts의 크기·SHA256, test-reports.tar.gz 규격은 유지한다. HTTP202는 완료가 아니다. 최종 SUCCEEDED와 wheel의 다운로드 검증으로 성공을 판단한다.

## 남은 연결과 완료 기준

1. 별도 Python Function handler 및 로컬 연결 테스트.
2. private 프로젝트용 Object Storage 소스 번들: 허용 버킷·객체 버전·SHA256 검증, 안전한 압축 해제, 최소 읽기 정책. 정적 Git 토큰을 전달하지 않는다. 현재 공개 Git 입력만으로 Launchpad private 소스를 빌드했다고 주장하지 않는다.
3. 별도 Python 빌더 이미지 준비·Function 배포·probe. 빌더 이미지 최초 제작과 Function 내부 사용자 소스 빌드는 서로 다른 단계다.
4. Companion Function 계약 조회·템플릿 생성·실행 화면에 Python을 연결. 준비 전에는 compatible로 표시하지 않는다.
5. Functions에서 실제 Python 테스트와 wheel 생성, Object Storage 결과 확인 및 Companion UI 검증.

컨테이너 이미지 생성은 별도 검증 항목이다. Functions 내 Docker daemon이 존재한다고 가정하거나 `python-v1` wheel 성공을 이미지 성공으로 표시하지 않는다. 이번 구현은 특정 앱·ADB·Langfuse에 종속되지 않는다.

## 2026-11-08 구현 확인

`src/main/resources/fn-python-template`에 독립 handler, Python 명령 구성, Dockerfile을 추가했다. 관리자가 `BUILD_MIN_FREE_BYTES`를 명시하고 요청도 `requiredFreeBytes`를 제공해야 빌드를 진행한다. `requirements-build.lock`의 중복·비고정 입력을 거부한다. pytest 실패 후 wheel을 만들지 않으며 JUnit XML을 기존 보고서 archive 형식으로 보관한다. 기존 Maven handler와 FunctionBuilds의 compatible 판정은 변경하지 않았다.

`python3 -m unittest discover -s tests -p test_python_function.py -v`의 4개 테스트가 통과했다. 명령 구성과 handler의 실패 기록 경로를 검증했고, OCI 저장소와 명령 실행은 테스트 대역이다. Functions의 실제 Python 빌드·wheel 다운로드 검증은 아직 하지 않았다. Dockerfile의 BASE_IMAGE는 공식 OCI Functions Python 3.12 이미지의 검증된 digest를 외부에서 제공해야 한다. 고정된 입력 없이 임의 최신 태그로 배포하지 않는다.

이 초기 버전은 전체 peak 메모리·디스크 사용량을 보장하지 않는다. 소스와 모든 의존성을 /tmp에 설치하므로 Launchpad 전체 의존성이 Functions 공간에 맞는지 먼저 probe 및 산정해야 한다. 필요하면 빌더 이미지에 고정 공통 의존성을 미리 넣고 변경되는 프로젝트 소스만 실행 공간에 두는 구조를 검토한다. 실행 중 원격 패키지 설치, 테스트, PEP 517 backend 실행은 신뢰하는 소스에만 허용한다. 결과 버킷 권한을 가진 Function에서 임의의 제3자 코드를 안전하게 실행하는 샌드박스로 간주하지 않는다.
