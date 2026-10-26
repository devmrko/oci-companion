# 리소스 중심 개편

2026-10-26 · Redmine [#977](https://redmine.cloud-handson.com/issues/977)

## 선택

기존 코드를 개편한다. 인증·Foundation·SDK·보안 기반을 유지한다.
프로젝트 등록이 선행되던 흐름을 리소스 조회부터 시작하도록 바꾼다.
기존 구현은 `baseline-2026-10-26` 태그와 `legacy` 실행 모드로 보존한다.

Java Foundation을 유지한다. 이번 범위에서 Python 재작성은 이점이 적다.
도메인과 저장소 포트에 SDK/JDBC 자료형을 노출하지 않는다.

## 화면과 동작

| 화면 | 현재 동작 | 후속 동작 |
|---|---|---|
| 연결 | OCI 프로파일 선택·연결·해제 | 인증 방식 확장 |
| 리소스 | 리전·구획, DevOps 목록, 조회 시각, 수동·주기 갱신 | Compute·Registry·네트워크 검색, 필터·상세 |
| 소스 | OCI/GitHub/GitLab 참조 등록·해제 | 인증·브랜치 조회·OCI 저장소 생성 |
| 빌드 | 기존 기능은 legacy 모드 | 소스 선택·명세 편집·파이프라인 구성·테스트·실행 |
| 배포본 | 설계 | OCIR/Artifact Registry 버전·출처·승격 |
| 배포 | 설계 | VM → Functions → OKE |
| 모니터링 | 설계 | 상태·대상별 작업·Logging |
| 네트워크 | 설계 | VCN·Subnet·NSG 선택·검사·구성 |
| 백업 | 설계 | 정책·대상·보관·복원 |
| 임시 워크로드 | 설계 | 용량 확인·주기·재시도·대체 자원·체크포인트 |

미구현 기능은 동작하는 메뉴처럼 표시하지 않는다. 문구는 짧은 기술 용어와 동작명으로 쓴다.

## 경계

- `CloudConnection`: 프로파일 목록과 인증.
- `ResourceCatalog`: 리전·구획·리소스 조회. OCI SDK / Demo 어댑터.
- `CatalogStore`: 캐시와 소스 저장. 현재 JSON 어댑터.
- `CatalogService`: 범위 분리, 갱신, 소스 검증.
- `PipelineGateway`, `DeploymentTarget`, `BuildRecipe`: 기존 계약. 후속 확장.

다음 단계에서 `SourceProvider`를 추가해 인증·브랜치·연결 조회를 제공자별로 구현한다.
현재 외부 주소는 저장만 하며 서버에서 접속하지 않는다. GitLab 자체 호스팅 주소도 등록 가능하다.

## 캐시 규칙

키: demo 여부 + 프로파일 + 테넌시 + 사용자 + 리전 + 구획. 파일명은 키의 SHA-256.
JSON에는 schema version, 범위, 성공 조회 시각, 최소 리소스 정보만 저장한다.

모든 DevOps 목록 조회가 성공한 경우에만 스냅샷을 교체한다. 일부 API 실패 시 전체 이전 스냅샷 유지.
따라서 현재는 프로젝트·저장소·빌드·배포 목록 권한이 모두 필요하다. 종류별 부분 갱신은 후속 확장이다.
조회 실패와 조회 결과 0건은 구분한다. 캐시 자체가 손상되면 재조회한다.
소스 파일 손상 시 원본 보존, 등록·삭제 차단. JSON 변경은 동일 디렉터리에서 원자적 교체한다.
동일 프로세스 갱신은 직렬 처리한다. 여러 프로세스 공유 쓰기는 지원하지 않는다.

리전·구획 선택지는 연결 세션에서 유지한다. '리전·구획 다시 조회'로 API 재조회한다.
리소스 캐시는 재시작 후 유지되지만 선택지는 새 연결 시 다시 조회한다.
자동 갱신은 열린 화면에서만 실행한다. 숨김 탭에서는 중지하고 입력 시 예약을 취소한다.
백그라운드 상주 스케줄러는 이번 범위에 포함하지 않는다.

## 도움말

각 화면에 Console 경로·절차, CLI, 권한, 오류, 공식 문서를 둔다.
프로파일·리전·구획은 현재 선택값으로 채우고 셸 인수를 인용한다. 명령은 자동 실행하지 않는다.

근거:
- [DevOps 프로젝트](https://docs.oracle.com/en-us/iaas/Content/devops/using/devops_projects.htm)
- [저장소 생성·조회](https://docs.oracle.com/en-us/iaas/Content/devops/using/create_repo.htm)
- [빌드 파이프라인 CLI](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-pipeline/list.html)

## 후속 설계 제약

배포 입력은 소스 → 빌드 → 배포본 → 대상 연결을 유지한다. 네트워크·백업을 대상 설정에 포함한다.
일반 VM, preemptible VM, Functions, OKE의 지원 작업을 구분한다.
용량 조회 결과를 preemptible 가용 보장으로 표시하지 않는다.
임시 자원의 체크포인트·산출물은 별도 영속 저장소에 둔다.

Redmine은 요구사항·완료 기준·검증, GitHub는 소스·설계·PR·CI·릴리스에 사용한다.
둘 다 앱 실행에 필요한 종속성으로 추가하지 않는다.
