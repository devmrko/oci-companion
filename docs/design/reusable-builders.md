# 재사용 빌더 이미지

2026-11-02 · [품질 기준](cicd-quality-criteria.md)의 빌드 환경 항목.

## 화면

빌드 → 환경 관리 → 빌드 구성 → 언어·빌드 도구 → 런타임 `빌더 컨테이너 사용`.

Go와 Ruby를 선택하면 빌더 컨테이너가 기본값이다. 기존 환경 사용도 선택할 수 있다. 이미지에는 태그 또는 digest를 지정한다. 공식 이미지 예시를 제공하며 사용자가 관리하는 OCIR 이미지로 바꿀 수 있다. 비공개 이미지 인증은 실행 환경의 설정이 필요하다.

| 언어 | 빌더 도구 | 의존성 기준 |
| --- | --- | --- |
| Java | JDK + Maven / Gradle | 프로젝트 설정, wrapper |
| Python | Python + pip / 프로젝트가 선택한 uv | requirements 또는 pyproject + uv.lock |
| JavaScript / TypeScript | Node + npm | package-lock.json, npm ci |
| Go | Go 컴파일러 + go mod | go.mod, go.sum |
| Ruby | Ruby + Bundler | Gemfile, Gemfile.lock |

현재 Python 생성 명령은 pip/pytest이다. uv는 이미지와 프로젝트 명령을 함께 구성해야 하며 UI의 uv 자동 생성 기능은 아직 없다. Go/Ruby를 위해 mise를 매 실행마다 설치하지 않는다. 도구 버전 관리가 필요하면 빌더 이미지를 만드는 과정에서 mise/ruby-build 등을 사용할 수 있다.

## 실행 구조

1. OCI Managed Build 실행기가 빌더 이미지를 pull한다.
2. 이미지 ID와 RepoDigests를 로그에 남긴다.
3. 빌더 컨테이너를 생성하고 프로젝트 작업 경로를 `/workspace`에 복사한다.
4. 빌드·테스트를 실행한다. 일반 환경변수와 Vault 참조로 해석된 변수는 이름으로 전달한다. 명세에 비밀값을 삽입하지 않는다.
5. 생성 파일·보고서를 작업 경로로 회수하고 컨테이너를 삭제한다. 실패 코드도 유지한다.
6. 산출물이 컨테이너 이미지인 경우, 회수된 결과로 OCI 실행기에서 별도 Dockerfile 빌드를 실행한다.

원격 Docker 호환 엔진에서도 동작하도록 bind mount 대신 copy를 사용한다. 기본 이미지는 `/bin/sh`가 필요하다. 회수 범위는 작업 경로 전체이며 대형 프로젝트에서는 복사 비용이 있다. 최소 권한 사용자로 실행 가능한 이미지는 해당 이미지의 USER 설정을 따른다. OCI 권한을 빌더 컨테이너에 자동 부여하지 않는다.

보고서 수집과 컨테이너 정리는 별도 shell 범위로 분리했다. 실패한 빌드 단계의 보고서를 회수해도 OCI 전달 단계가 실행된다는 뜻은 아니다.

## 재현성과 속도

공식 태그는 시작용 선택값이다. 운영 설정은 검증한 digest와 의존성 lock 파일을 커밋한다. Ruby 예제의 최초 lock 생성은 재현성 완료로 판정하지 않는다. 사용자 소스에 lock 파일을 저장하고 frozen 모드를 쓰는 정책은 후속 보완이다.

빌더 이미지 재사용은 런타임 설치를 생략한다. 첫 pull과 프로젝트 의존성 다운로드는 남는다. 현재 실행 간 영구 캐시를 구성하지 않았으므로 캐시 속도 개선을 주장하지 않는다. 빌더 이미지와 배포 이미지는 별도로 관리한다.

## OCI에서 수행

콘솔: DevOps → 프로젝트 → 빌드 파이프라인 → Managed Build → 빌드 명세 경로.
명세의 `docker pull/create/cp/start/inspect/rm`은 Managed Build 안에서 실행된다. 실행기 자체를 사용자 이미지로 교체하는 기능이 아니다.

Registry 인증·네트워크 오류는 선택한 이미지 저장소 접근을 확인한다. 빌더에 언어 도구가 없으면 해당 이미지의 Dockerfile을 수정하고 새 버전을 게시한다. 소스 코드는 빌더 이미지에 미리 넣지 않는다.

## 실제 실행 증거 — 2026-11-02

- DEFAULT / ap-seoul-1 / Joungmin.Ko, `companion-builder-automation-validation`.
- 실제 OCI OL8 빌드 실행기에서 Go·Ruby 설치 없이 공식 빌더 컨테이너 실행 성공.
- Go 1.27.3 `go test ./...` 통과 및 `dist/application` 생성·회수. Ruby 3.4.12 HTTP 200/404/405 검사 3건 통과 및 JUnit 회수. Registry 보고서 전달 후 앱 집계 확인.
- 빌더 이미지 ID: Go `f5d48661d11d8c3f1e8a3b477f2865c72b4ff7651a7c593d49f4b77b2fc1a804`, Ruby `6b7ec3348367b26055c0c90a0d1de326dcb1b0e68bfe86d9b54bdaee92ac6644`. RepoDigests도 실제 로그에 기록됨. 이미지 ID와 registry manifest digest는 구분한다.
- 시험 범위는 x86. 이 기능의 ARM 빌더 실행은 미검증이다.
