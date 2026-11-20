# 화면에서 컨테이너 빌드 → OKE 배포 검증

2026-11-20, DEFAULT / 서울 / 검증 구획. 기존 클러스터의 전용 namespace를 사용했다. 새 클러스터·노드는 생성하지 않았다.

## 실제 결과

| 단계 | 결과 |
| --- | --- |
| 빌드 실행 | 화면에서 `companion-container-demo-x86` 실행, SUCCEEDED |
| 이미지 저장 | OCI 전달 단계 SUCCEEDED, OCIR에 새 digest 확인 |
| 배포 구성 | 화면에서 환경·이미지·namespace·사양 선택, 명세 확인 후 OCI 저장 |
| 실행 사양 | amd64, 복제 1, CPU 250m, 메모리 256MiB, HTTP 8080, `/health` 준비 검사 |
| OKE 배포 | 새 파이프라인 `companion-oke-flow-20261120`, SUCCEEDED |
| 앱 상태 | 화면에서 Pod Running / Ready, 재시작 0, 할당 노드 Ready |
| HTTP | 임시 localhost 포트 전달 후 브라우저 `/hello` 응답: status=ok, architecture=x86_64 |
| 로그 | 앱의 Pod 로그 화면에서 `/health`, `/hello` HTTP 200 확인 |
| 이미지 일치 | 배포 명세의 고정 digest와 실행 Pod imageID 일치 |

검증 digest: `sha256:1f900b1bc1087a3bfe432bf0f513dfa8a3fed7647ef24cd802c763fdf65e0a13`.

빌드·배포 실행, 사양 선택, 조회 연결 등록, 상태·로그 확인은 실제 앱 화면에서 수행했다. SDK·kubectl은 인증 준비, 제한된 IAM 추가, 읽기 진단, 포트 전달, 정리에 사용했다. 비밀값은 화면·문서에 입력하지 않았다.

## 화면 보완

컨테이너 산출물만 있는 실행도 VM 파일 일치 검증 경로로 연결됐다. 파일 산출물이 있는 성공 실행에만 해당 경로를 제공하도록 변경했다. 컨테이너는 ‘배포 대상 선택’으로 이동한다. 컨테이너 빌드와 배포의 자동 일치 검증을 구현한 것은 아니다.

로그 자동 갱신과 실행 상태 조회를 구분했다. 실행 상세에 ‘상태·산출물 다시 조회’를 추가하고, 기존 선택란은 ‘로그 자동 갱신’으로 표시한다. 페이지 전체 자동 갱신은 추가하지 않았다.

Java 테스트 273개 통과. 실제 성공 빌드 화면에서 새 링크·버튼을 확인했다. 390px 화면 가로 넘침 없음. 기존 파일 빌드의 실행 ID 유지 검증도 통과했다.

## 검증 범위

기존 x86 OKE, 사설 OCIR, 단일 Deployment·ClusterIP Service 경로다. ARM 노드, 공개 Ingress, 부하·확장, 이번 이미지의 버전 간 롤백, 신규 테넌시 최초 구축은 포함하지 않는다. 이미지 digest는 이번 실행 후 OCIR에서 선택했다. 빌드 API의 전달 결과에는 digest가 비어 있어 자동 추적의 근거로 사용하지 않았다.

테스트 보고서 아카이브는 빌드 출력에 있지만 이 파이프라인의 Registry 전달 대상은 컨테이너 이미지다. 이번 검증으로 보고서 화면 집계까지 통과했다고 보지 않는다.

## 정리

검증용 조회 연결은 화면에서 해제했다. 임시 namespace·pull Secret, 새 파이프라인에 한정한 IAM 정책, 포트 전달·임시 kubeconfig를 정리했다. namespace와 정책의 삭제를 재조회로 확인했다. 빌드·배포 이력과 OCI 구성은 보존하며, 재실행하려면 인증·권한을 다시 준비해야 한다.

로컬 증거: `.tools/oke20261120-state.json`, `oke20261120-pods.json`, `oke20261120-log-result.json`, `oke20261120-cleanup.json`. 인증·리소스 상세를 포함할 수 있어 Git에 올리지 않는다.
