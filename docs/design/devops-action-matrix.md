# OCI DevOps 액션·입력값 대조

검토일: 2026-10-27. 구현 기준: commit 58167a7 / 실행 서버 8091.
사용자 지적: 실행 화면의 저장소 누락. 화면 수정에 앞서 공식 액션·입력값·선행 조건을 대조한다.
작성 중이던 실행 탭 수정은 미배포 작업본이며 아래 구현 완료 판정에 포함하지 않는다.
이후 실행 저장소·Managed Build 생성·명세 초안·목적 검증을 보완했다. 아래 표는 최초 검토 시점이며 최신 상태는 [보완 검증](../build-gap-validation.md)을 따른다.

이 명세를 반영한 [빌드·저장·배포 화면 설계](build-workflow.md): 탭·입력·저장 대상·실행 검증·완료 기준.

## 판정 기준

- **필수**: API/CLI가 요구하는 값. 상위 리소스 선택으로 채우는 값도 포함한다.
- **조건부 필수**: 사용자가 선택한 기능을 수행하려면 필요한 값.
- **선택/기본값**: OCI 기본값을 사용할 수 있다. 생략하더라도 실제 적용값을 표시한다.
- **앱 입력**: Maven goal/profile 같은 편의 입력. OCI API 필드로 가장하지 않고 빌드 명세/명령으로 변환한다.
- **선행 조건**: IAM, 활성 리소스, 로그, 접근 가능한 소스, 올바른 단계 연결. 단순한 필드 유무와 구분한다.

CLI의 `Required Parameters`만으로 화면을 만들면 부족하다. 중첩 JSON의 내용과 기능별 조건도 검사해야 한다.
`--generate-param-json-input` 등 CLI 보조 옵션은 업무 필수값이 아니다.
문서의 latest 페이지 버전은 페이지마다 다르다. 앱 SDK는 3.94.1이며 지원 여부는 실제 API로도 검증한다.

## 저장소 누락의 정확한 의미

Managed Build는 빌드·테스트 단계다. outputArtifacts는 선택 사항이며 보관 기간은 해당 build run 수명이다.
지속적으로 보관·배포하려면 Deliver Artifacts, Registry, DevOps 산출물 참조를 연결해야 한다.
따라서 요청한 **빌드 → 저장 → 배포 흐름에서는 저장소 지정이 조건부 필수**다.
[빌드 사양](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm), [전달 단계](https://docs.oracle.com/en-us/iaas/Content/devops/using/add_deliverartifact.htm).

`CreateBuildRun`은 기존 파이프라인을 실행한다. API 필수 입력은 파이프라인 ID이며,
실행별 파라미터·커밋·실행 이름은 선택 사항이다. Registry를 직접 전달하는 실행별 인자는 없다.
저장 위치는 파이프라인/산출물 참조에서 결정되므로 실행 화면에 현재값과 변경 영향을 보여줘야 한다.
[실행 API 대응 CLI](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-run/create.html).

## 액션 범위

| 리소스 | 액션 묶음 | 앱 상태 |
|---|---|---|
| 프로젝트 | 목록·상세·생성·수정·이동·삭제 | 조회·생성만 |
| 소스 저장소 | 목록·상세·생성·수정·미러 동기화·브랜치/커밋·파일 | OCI 조회와 외부 URL 로컬 등록 중심 |
| 외부 연결 | 공급자별 생성·조회·수정·검증·삭제 | 미구현 |
| 빌드 파이프라인 | 생성·조회·수정·삭제, 파라미터 | 생성·조회·일부 수정 |
| 빌드 단계 | Managed Build / Deliver Artifacts / Trigger Deployment / Wait 생성·조회·수정·삭제 | Managed Build 생성 누락, 기존 단계 일부 수정; 전달 생성/연결만 |
| 빌드 실행 | 생성·조회·취소·메타데이터 수정 | 생성·조회; 취소 미구현 |
| 트리거 | 공급자별 생성·조회·수정·삭제 | 미구현 |
| 산출물 참조 | 파일·이미지·inline·Helm 생성·조회·수정·삭제 | 파일·이미지 생성/조회 및 전달 연결 |
| 배포 환경·파이프라인·단계 | 생성·조회·수정·삭제 | 현재 기본 모드의 관리 UI 미구현 |
| 배포 실행 | 전체/단일 단계 실행·재배포·승인·취소·조회 | 현재 기본 모드 UI 미구현 |
| 비동기 작업 | work request 상태·오류·로그 | 전용 추적 UI 미구현 |

근거: [공식 DevOps 액션 목록](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops.html).
PR·리뷰·댓글·보호 브랜치도 DevOps 형상관리 기능이다. 위 표는 앱의 CI/CD 실행 경로 중심이며 해당 형상관리 전체 폼까지 검토 완료한 것은 아니다.
Notifications·Logging·Registry·IAM은 연관 서비스다. DevOps 단계와 혼동하지 않는다.

## 빌드 단계별 요구값

| 액션 | API 필수 | 조건부 필수·선행 조건 | 선택/기본값 | 필요한 화면 |
|---|---|---|---|---|
| Managed Build | 파이프라인, 소스 목록, 빌드 이미지, 선행 단계 목록 | 소스별 유형·이름·저장소/연결·브랜치; 유효한 빌드 사양 | runner shape/CPU/메모리, 제한 시간, 명세 경로, 주 소스, 사설 네트워크 | 소스·도구·명령·환경·출력 설정 |
| Deliver Artifacts | 파이프라인, 전달 매핑 목록, 선행 단계 목록 | 앞선 Managed Build, outputArtifacts 이름/유형, 대상 산출물 참조, Registry 접근 권한 | 이름·설명·태그 | 출력별 저장소·경로·버전과 매핑 |
| Trigger Deployment | 빌드 파이프라인, 배포 파이프라인, 전체 파라미터 전달 여부, 선행 단계 | 실행 가능한 배포 파이프라인·환경·산출물 | 이름·설명·태그 | 대상 파이프라인·인자 전달 선택 |
| Wait | 파이프라인, 선행 단계, waitCriteria | 기준 유형에 맞는 대기 시간 | 이름·설명·태그 | 대기 시간 |

필수 키와 선택 필드는 아래 부록의 개별 CLI 문서를 대조했다.
소스가 여러 개면 기본 주 소스는 첫 항목이다. UI에서는 어느 저장소의 명세를 쓰는지 명시한다.
기본 명세 경로는 build_spec.yaml. 다른 경로를 쓰면 경로 지정이 필요하다.
[Managed Build 생성](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-pipeline-stage/create-build-stage.html).

소스 선택은 Git 주소만 받는 것으로 끝나지 않는다. GitHub/GitLab 등 OCI 네이티브 연결은 사전에 연결 리소스를 준비한다.
GitHub 연결의 PAT 입력은 비밀 문자열이 아니라 Vault에 저장한 secret OCID다.
[소스 선택](https://docs.oracle.com/en-us/iaas/Content/devops/using/add_buildstage.htm), [GitHub 연결](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/connection/create-github-connection.html).

## 빌드 사양과 도구 입력

| 분류 | 필요값 | 화면 처리 |
|---|---|---|
| 사양 공통 | version=0.1, component=build, steps | 명세 유효성 확인/미리보기 |
| 명령 단계 | type=Command, 실행 command | 작업 경로·설치·테스트·빌드 명령 |
| 환경 | 일반 변수, Vault 변수, 후속 단계로 내보낼 변수 | 세 종류를 구분; 비밀값 직접 저장 금지 |
| 입력 산출물 | 사용 시 name/type/location, 공급원별 URL 또는 artifact ID/저장소·경로·버전 | 필요할 때만 입력 |
| 출력 산출물 | 저장/후속 전달 시 name/type/location | 실제 빌드 출력과 저장 목적지를 분리 |
| Maven/Gradle/Python/Node | 도구·버전·명령·profile/옵션 | 앱 템플릿 → 명세 명령; OCI 전용 필드가 아님 |
| 결과 | 명령 종료 상태·로그; 보고서 파일이 있으면 테스트 집계 | 로그 확인과 테스트 보고서 파싱을 구분 |

근거: [빌드 사양 구조와 필수 조건](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm).
Maven goal/profile, Gradle task, pytest 옵션, npm script를 단순한 이름/값 표로 대체하지 않는다.
저장소의 기존 명령이 원본이며 템플릿 적용은 명세 변경으로 표시한다. 지원하는 런타임 목록은 별도 조회/문서 대조가 필요하다.

## Registry 전달 입력

| 대상 | 필요값 | 연결 확인 |
|---|---|---|
| 파일 | Registry 저장소, 파일 경로, 버전, 프로젝트, GENERIC_FILE 참조 유형, 변수 치환 모드 | outputArtifacts의 BINARY 이름 → DevOps 참조 |
| 이미지 | 이미지 주소/태그, 프로젝트, DOCKER_IMAGE 참조 유형, 변수 치환 모드 | outputArtifacts의 DOCKER_IMAGE 이름 → DevOps 참조 |
| 공통 | 전달 단계·선행 단계·산출물 이름/참조 ID 매핑 | 다른 출력 연결을 덮어쓰지 않음 |

근거: [파일 참조](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-artifact/create-generic-artifact.html), [이미지 참조](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-artifact/create-ocir-artifact.html).
이미지 참조 API는 URI를 받는다. 앱의 Registry 선택은 URI를 구성하는 편의 기능이다.
버전 표현식은 pipeline parameter 또는 exported variable과 연결하고, 미정의 변수를 실행 전에 확인해야 한다.
덮어쓰기 금지 저장소에는 고유 버전이 필요하다. Registry 생성, 참조 생성, 파일 업로드는 서로 다른 액션이다.
[전달 버전](https://docs.oracle.com/en-us/iaas/Content/devops/using/deliverartifact-stage.htm).

## 선행 서비스

| 액션 | 필수·조건 | 현재 누락 |
|---|---|---|
| 알림 주제 생성 | 구획·이름 | 구독 생성/확인 UI 없음 |
| 프로젝트 생성 | 구획·이름·notificationConfig(topicId) | 로그 준비를 생성 흐름에서 연결하지 않음 |
| DevOps 로그 준비 | 로그 그룹·로그 이름·SERVICE 유형, DevOps 프로젝트에 맞는 source/configuration, 활성화 | 콘솔 안내만 있음 |
| 로그 검색 | 검색 쿼리·시작/종료 시각, 로그 접근 권한 | 조회 구현; 실행 전 활성화 점검 부족 |
| 사용자 권한 | 해당 리소스 조회·생성·수정/실행 권한 | 안내만; 유효 권한 전체 판정 불가 |
| 실행 파이프라인 권한 | 소스·Vault·산출물 참조·Registry·배포 대상 접근 | 사용자 연결 성공과 구별해야 함 |

근거: [주제 생성](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/ons/topic/create.html), [프로젝트](https://docs.oracle.com/en-us/iaas/Content/devops/using/create_project.htm), [로그 생성](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/logging/log/create.html), [로그 검색](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/logging-search/search-logs.html), [IAM](https://docs.oracle.com/en-us/iaas/Content/devops/using/devops_iampolicies.htm).
프로젝트 문서는 파이프라인 실행 전에 Logging 활성화를 요구한다. 알림 주제를 로그 저장소로 취급하지 않는다.
권한은 읽기 점검으로 확인 가능한 범위만 표시한다. 실제 실행 권한 전부를 확인했다고 주장하지 않는다.

## 현재 구현의 결손

| 우선순위 | 결손 | 수정 기준 |
|---|---|---|
| 1 | 실행 화면에 산출물 저장소 선택 없음 | 현재값/목적지/버전/연결 상태를 실행 전에 표시 |
| 1 | 파이프라인 생성 후 Managed Build 생성 UI 없음 | 소스·이미지·명세·선행 단계로 실제 BUILD 단계 생성 |
| 1 | 언어별 명령 설정 없음 | 도구 선택 → 실제 명령/명세 생성·미리보기·저장 |
| 1 | 외부 Git URL 로컬 등록만으로 빌드 소스 연결을 대신함 | OCI Connection·Vault·저장소·브랜치를 연결 |
| 1 | 저장/배포 목적에 따른 필수값 검증 없음 | 실행 목적에 맞춰 출력·참조·전달 단계 누락 차단 |
| 1 | 로그 활성화·변수 치환·저장 버전 충돌 확인 부족 | 실행 준비 상태로 각각 표시; 미확인은 성공으로 취급하지 않음 |
| 2 | 실행 취소 없음 | run ID·취소 사유·현재 상태 확인 |
| 2 | Wait/Trigger Deployment/자동 트리거 없음 | 액션별 입력·선행 조건·권한 적용 |
| 2 | 테스트 보고서 요약 없음 | 실제 보고서 형식별 파싱; 테스트 goal만으로 성공 수치 생성 금지 |
| 후속 | VM/Functions/OKE 배포 폼 없음 | 아래 환경·단계 필수값부터 설계 |

실제 db-companion 한 건의 성공은 범용 빌드 생성 흐름 완성을 뜻하지 않는다.
근거 코드: BuildAdministration/BuildSetup/BuildControl/Registry 포트, OciBuildAdministration/OciBuildControl 어댑터,
CatalogPages, builds.html, build-setup.html (58167a7). legacy 프로파일 기능을 현재 기본 모드 완료로 세지 않는다.

## 화면 설계 기준안

실행 화면에는 **소스 → 빌드/테스트 → 산출물 저장 → 배포 연결 → 실행 준비 상태**를 한 흐름으로 표시한다.
각 항목은 필수/조건부/선택/기본값/권한미확인을 구분한다.

- 빌드·테스트만: Registry 저장 생략을 명시한다.
- 빌드·저장: 출력 이름/유형/위치, Registry 목적지, 버전, 전달 단계 연결이 필요하다.
- 빌드·저장·배포: 위 조건과 실행 가능한 배포 파이프라인/환경이 필요하다.
- 기존값을 쓰는 경우에도 현재 소스·명령·저장소·버전을 숨기지 않는다.
- 이번 실행 인자와 OCI 기본 설정 변경을 구분한다. 저장소 변경을 이번 실행 전용 override인 것처럼 표시하지 않는다.
- 생성/수정 후 work request와 실제 단계 반영을 확인한 뒤 실행한다.
- 도움말은 액션별 콘솔 위치, CLI 필수/선택 인자, 권한, 성공/실패 확인 방법으로 구성한다.

위 실행 목적 선택과 화면 배치는 검토 결과에 따른 설계안이다. 아직 구현/배포하지 않았다.

## 부록: 주요 액션 21개의 필수·선택 인자

공통 CLI 보조 인자·태그는 생략. JSON 내부 조건은 위 본문과 개별 모델 문서를 함께 확인한다.
VM·Functions·OKE는 기본 배포 경로만 대조했으며 Canary/Blue-Green/Helm/승인/Shell의 전체 필드는 후속 명세 대상이다.

| 액션·공식 명세 | 필수 인자 | 선택 업무 인자 |
|---|---|---|
| [build-pipeline-stage/create-build-stage](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-pipeline-stage/create-build-stage.html) | `--build-pipeline-id`, `--build-source-collection`, `--image`, `--stage-predecessor-collection` | `--build-runner-config`, `--build-spec-file`, `--description`, `--display-name`, `--network-channel`, `--primary-build-source`, `--stage-execution-timeout-in-seconds` |
| [build-pipeline-stage/create-deliver-artifact-stage](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-pipeline-stage/create-deliver-artifact-stage.html) | `--build-pipeline-id`, `--deliver-artifact-collection`, `--stage-predecessor-collection` | `--description`, `--display-name` |
| [build-pipeline-stage/create-trigger-deployment-stage](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-pipeline-stage/create-trigger-deployment-stage.html) | `--build-pipeline-id`, `--deploy-pipeline-id`, `--is-pass-all-parameters-enabled`, `--stage-predecessor-collection` | `--description`, `--display-name` |
| [build-pipeline-stage/create-wait-stage](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-pipeline-stage/create-wait-stage.html) | `--build-pipeline-id`, `--stage-predecessor-collection`, `--wait-criteria` | `--description`, `--display-name` |
| [build-run/create](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-run/create.html) | `--build-pipeline-id` | `--build-run-arguments`, `--commit-info`, `--display-name`, `--if-match` |
| [build-run/cancel](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-run/cancel.html) | `--build-run-id`, `--reason` | `--if-match` |
| [project/create](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/project/create.html) | `--compartment-id`, `--name`, `--notification-config` | `--description` |
| [build-pipeline/create](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/build-pipeline/create.html) | `--project-id` | `--build-pipeline-parameters`, `--description`, `--display-name` |
| [repository/create](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/repository/create.html) | `--name`, `--project-id`, `--repository-type` | `--default-branch`, `--description`, `--mirror-repository-config`, `--parent-repository-id` |
| [connection/create-github-connection](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/connection/create-github-connection.html) | `--personal-access-token`, `--project-id` | `--description`, `--display-name` |
| [deploy-artifact/create-generic-artifact](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-artifact/create-generic-artifact.html) | `--argument-substitution-mode`, `--artifact-path`, `--artifact-type`, `--artifact-version`, `--project-id`, `--repository-id` | `--description`, `--display-name` |
| [deploy-artifact/create-ocir-artifact](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-artifact/create-ocir-artifact.html) | `--argument-substitution-mode`, `--artifact-type`, `--project-id`, `--source-image-uri` | `--description`, `--display-name`, `--image-digest` |
| [trigger/create-devops-code-repo-trigger](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/trigger/create-devops-code-repo-trigger.html) | `--actions`, `--project-id` | `--description`, `--display-name`, `--repository-id` |
| [deploy-environment/create-compute-instance-environment](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-environment/create-compute-instance-environment.html) | `--compute-instance-group-selectors`, `--project-id` | `--description`, `--display-name`, `--security-attributes` |
| [deploy-environment/create-function-environment](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-environment/create-function-environment.html) | `--function-id`, `--project-id` | `--description`, `--display-name`, `--security-attributes` |
| [deploy-environment/create-oke-cluster-environment](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-environment/create-oke-cluster-environment.html) | `--cluster-id`, `--project-id` | `--description`, `--display-name`, `--network-channel`, `--security-attributes` |
| [deploy-stage/create-deploy-compute-instance-group-stage](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-stage/create-deploy-compute-instance-group-stage.html) | `--compute-instance-group-environment-id`, `--deployment-spec-artifact-id`, `--pipeline-id`, `--rollout-policy`, `--stage-predecessor-collection` | `--artifact-ids`, `--description`, `--display-name`, `--failure-policy`, `--load-balancer-config`, `--rollback-policy` |
| [deploy-stage/create-deploy-function-stage](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-stage/create-deploy-function-stage.html) | `--docker-image-artifact-id`, `--function-environment-id`, `--pipeline-id`, `--stage-predecessor-collection` | `--config`, `--description`, `--display-name`, `--function-timeout-in-seconds`, `--max-memory-in-mbs` |
| [deploy-stage/create-deploy-oke-stage](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-stage/create-deploy-oke-stage.html) | `--kubernetes-manifest-artifact-ids`, `--oke-cluster-environment-id`, `--pipeline-id`, `--stage-predecessor-collection` | `--description`, `--display-name`, `--namespace`, `--rollback-policy` |
| [deployment/create-pipeline-deployment](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deployment/create-pipeline-deployment.html) | `--pipeline-id` | `--artifact-override-arguments`, `--deployment-arguments`, `--display-name`, `--dry-run`, `--stage-override-arguments` |
| [deploy-pipeline/create](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deploy-pipeline/create.html) | `--project-id` | `--description`, `--display-name`, `--pipeline-parameters` |
