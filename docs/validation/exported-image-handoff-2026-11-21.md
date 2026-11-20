# 빌드 이미지 출력 → OKE 실검증

2026-11-21 KST, DEFAULT / 서울 / 검증 구획에서 브라우저로 빌드·배포를 실행했다. OCI 기록에서 읽은 이미지 digest를 배포 명세와 비교하고 실제 OKE 앱 응답까지 확인했다.

## 구현

- OCI Managed Build의 `env.exportedVariables`를 사용한다. 앱 규약 `BUILD_IMAGE_REFERENCE`에 업로드 결과의 digest 주소를 기록한다.
- 빌드 이력은 성공한 단계의 해당 변수만 이미지로 표시한다. 태그 전용 값·중복 이름·실패 단계는 연결 근거로 사용하지 않는다.
- 배포 대상 확인과 실행 직전에 빌드 기록·명세를 비교한다. OCI 전달 단계가 없어도 출력 이미지로 OKE 연결을 검증할 수 있다. 파일 배포의 전달 단계 조건은 유지한다.
- [업로드 템플릿과 설정](../build/image-digest-output.md)은 앱 서버 없이 실행된다. Vault 인증 파일을 사용하는 skopeo 업로드 결과를 기록한다. 비밀값은 출력 변수에 넣지 않는다.

## 화면 검증

| 단계 | 확인 결과 |
| --- | --- |
| 빌드 실행 | `companion-image-output-20261121`, 재시도 실행 `image-digest-output-retry-20261121` 성공 |
| 빌드 출력 | `BuildStageRunProgress.exportedVariables`의 digest 주소를 앱에서 표시 |
| 다른 배포 이미지 | 기존 `companion-oke-flow-20261120` 선택 시 일치 확인 실패, 실행 버튼 없음 |
| 일치하는 이미지 | 새 OKE 명세를 화면에서 구성하고 해당 파이프라인 선택. 이미지 1개 일치 |
| 배포 실행 | 화면의 ‘확인한 배포 실행’으로 요청, OCI `SUCCEEDED` |
| 앱 상태 | `hello` 목표 1 / Ready 1 / 최신 배포본 1, Pod Running, 이미지 ID 일치 |
| HTTP | 브라우저에서 포트 포워딩한 `/hello`: `status=ok`, `architecture=x86_64` |
| 로그 | OCI Logging의 `OKE deployment succeeded`, Pod 로그 화면의 `GET /hello HTTP/1.1` 200 확인 |

확인한 digest: `sha256:90cfaacf5f6db0a8a17c0677339f6e6c312a8262a1d3d782dc6af6d4e01ca653`.

처음 빌드는 소스 조회 권한 부족으로 실패했다. 검증 파이프라인과 저장소 ID로 한정한 `read devops-repository` 정책을 추가한 뒤 화면에서 재실행했다. 배포에는 기존 클러스터의 전용 namespace를 사용했다. VM·노드·클러스터는 새로 만들지 않았다.

검증 서버의 kubectl 경로가 기본값으로 남아 초기 Deployment 조회가 실패했다. `COMPANION_KUBECTL=/opt/homebrew/bin/kubectl` 설정 후 재시작하고 화면에서 연결·상태·로그 조회를 완료했다.

## 범위

이번 검증은 x86 이미지 하나, 인라인 명세의 단일 OKE 배포 단계다. 새 OKE 명세 구성은 별도 화면에서 수행했다. 빌드 결과로 새 배포 구성을 자동 생성하거나 Container Instances·Functions 배포를 검증한 것은 아니다. 출력 변수는 빌드 생산자의 선언이며 서명·소스 출처 증명이 아니다.

Java 테스트 282개 통과. 누락·중복·태그 값·실패 단계, 이미지 불일치·명세 변경 차단과 기존 파일 배포 검증을 포함한다.

## 정리

검증 namespace·Pod·Service·pull Secret, 임시 IAM 정책 2개, 로컬 조회 연결·kubeconfig·포트 포워딩을 제거했다. namespace 및 정책 삭제를 재조회로 확인했다. 빌드·배포 이력, 소스 검증 브랜치와 Registry 이미지는 검토용으로 남겼다. 다시 실행하려면 전용 namespace·pull Secret·실행 정책을 준비해야 한다. 기존 앱·클러스터·노드·인증 토큰은 변경하지 않았다.
