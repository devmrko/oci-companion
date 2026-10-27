# ARM Function 직접 빌드 검증

2026-10-27 · Redmine #997 · DEFAULT / ap-seoul-1 / Joungmin.Ko

**성공.** db-companion을 OCI ARM Function 안에서 다운로드·컴파일·테스트하고 결과를 Object Storage에 저장했다. VM 빌드나 로컬 대체 실행이 아니다.

| 항목 | 실측 |
|---|---|
| 실행 아키텍처 | `aarch64` |
| 메모리 제한 | 3GiB |
| 최대 메모리 | 1,339,478,016 bytes, 약 1.25GiB — cgroup 최대값 |
| `/tmp` 크기 | 768MiB |
| 종료 직전 작업 파일 합계 | 약 217.48MiB — 최고 사용량은 아님 |
| 전체 처리 시간 | 142.49초 — Function 핸들러 기준, 초기 컨테이너 시작 제외 |
| Maven 시간 | 1분 52초 |
| Java 테스트 | 1,444개, 실패·오류·건너뜀 0 |
| 품질 검사 | PMD 통과 |
| JAR | 61,111,724 bytes |
| 결과 저장 | JAR, 테스트 보고서, 빌드 로그, 결과 JSON |

## 실행 조건

- 소스: `https://github.com/devmrko/db-companion.git`
- 커밋: `7a5cb34b68d8dae90f9c398667a0db976bb2f6d2`
- 명령: `mvn -B -ntp -Pquality -Dmaven.repo.local=<실행 전용 경로> -DargLine=-Xmx768m clean verify`
- JDK 21.0.12.1 / Maven 3.9.9, Maven 힙 768MiB, 테스트 JVM 힙 768MiB
- 신규 작업 디렉터리와 빈 Maven 저장소 사용. 소스와 의존성을 실행 중 다운로드.
- Detached 호출, 제한 3600초. 접수 HTTP202가 아닌 최종 결과·로그·산출물로 성공 판정.
- Function 호출 전 probe에서 HTTP200과 아키텍처·실제 제한 확인.
- JavaScript 테스트, 컨테이너 이미지 생성, 배포, 앱 GUI 연결은 이번 검증 범위에 포함하지 않음.

## 근거

- 애플리케이션: `oci-companion-arm-build-test`
- Function: `db-companion-builder`
- Function OCID: `ocid1.fnfunc.oc1.ap-seoul-1.amaaaaaavsea7yiakod2mbauksgad3d5e27lq3d5vb7rm4todnv55h5zt7ra`
- 실행: `function-arm-5895c60c-0695-48d2-83dd-8900450c0e91`
- 버킷: `oci-companion-arm-build-997`, namespace `apackrsct01`
- 객체: `<실행>/result.json`, `build.log`, `db-companion.jar`, `test-reports.tar.gz`
- JAR SHA256: `a504f2e60b1814945d7502342e396a3578c3b1a378cc18c8c97c7ac39d1bead1`
- 업로드된 JAR을 다시 다운로드해 크기·SHA256 일치 확인. Surefire XML을 합산해 테스트 수 확인.
- 실행 이미지: `icn.ocir.io/apackrsct01/companion/arm-function-builder@sha256:b794f02b1eb237eda58ed98f2c32643b6ffb33f7d8dc1dea83a535421d9275a7`
- 사용한 베이스 이미지: `container-registry.oracle.com/oci_functions/python:3.12`, digest `sha256:a51e9a800571837471de03253d4100f05d8dbd6f5a6a22e2118854cbe75b1163`
- 구현: [Function 코드](../examples/build/function-arm/func.py), [준비·실행](../examples/build/function-arm/README.md)

## 운영 범위

전용 Function의 Resource Principal에 전용 버킷 객체 생성·덮어쓰기만 허용했다. Function 로그를 OCI Logging에 연결했다. Registry 이미지 업로드용 임시 Auth Token은 삭제했다. 검증용 Function·이미지·버킷·로그·정책은 재사용을 위해 유지하며 Provisioned Concurrency는 설정하지 않았다. 기존 VM은 변경하지 않았다.

서울 API에서 코드-only `sourceType=ARCHIVE` 생성은 `400 InvalidParameter: Invalid typeId provided ARCHIVE`로 거부됐다. 따라서 이미지 기반 Function으로 검증했다. 문서의 기능 설명만으로 이 리전의 실제 지원을 확정하지 않는다.

## 설계 반영 기준

이 프로젝트의 Maven 빌드는 3GiB Function으로 충분했다. 대형 프로젝트 전체에 일반화하지 않는다. 입력 화면에서 빌드 시간·메모리뿐 아니라 임시 공간도 표시해야 한다. 이미지 생성은 이번 결과로 지원을 주장하지 않는다.

Function 빌드 실행기는 Managed Build·VM 실행기와 별도 인터페이스로 연결한다. 호출 접수, 빌드 진행, 산출물 저장을 구분한다. 이번 저장 위치는 Object Storage이며 Artifact Registry 전송·GUI 연결은 후속 작업이다.
