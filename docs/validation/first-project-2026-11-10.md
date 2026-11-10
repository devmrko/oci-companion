# 새 프로젝트 최초 구성 검증

2026-11-10 KST, DEFAULT / ap-seoul-1 / Joungmin.Ko. 빈 로컬 데이터의 alpha.8 ZIP을 8092에서 실행했다. 기존 DevOps 프로젝트·파이프라인·코드/산출물 저장소를 재사용하지 않았다.

## 화면에서 생성한 구성

알림 주제(구독 없음), 프로젝트, OCI 코드 저장소, 로그 그룹, 활성 프로젝트 로그, 빌드 파이프라인, Generic Artifact Registry, 관리형 빌드 단계, 애플리케이션·보고서 산출물 참조와 전달 연결을 생성했다. VM 배포 환경도 새 프로젝트 안에 생성했으며 실행 VM은 기존 검증 전용 VM을 재사용했다.

기존 Gitea 검증 예제의 정확한 커밋을 Git CLI로 새 OCI 저장소의 검증 브랜치에 전송했다. force push·기존 저장소 변경·새 인증 발급은 하지 않았다. Git 전송은 앱 기능으로 수행한 것이 아니며 기존 SSH 인증을 사용했다. UI 생성 저장소의 프로젝트 ID를 확인한 후 전송했다.

## 발견한 문제와 수정

첫 실행은 `build_spec.yaml` 읽기에서 실패했다. 사용자 프로파일의 동일 객체 읽기는 200이었으며, Audit에는 새 빌드 파이프라인의 객체 조회 200과 `GetObjectContent` 404가 기록됐다. 파일 부재로 단정하거나 OCI 오류에 제시된 tenancy 전체 관리 권한을 적용하지 않았다.

검증 파이프라인 하나·현재 구획·`GetObjectContent` 읽기에 한정한 임시 권한을 추가했다. 이후 재실행이 명세 읽기와 빌드·테스트를 통과했다. 정책 조건 변수의 API별 제공 범위와 IAM 전파 지연의 영향을 독립적으로 분리한 실험은 아니다. 모든 환경에 동일 정책이 필요하다고 일반화하지 않는다.

재실행의 빌드는 성공했지만 산출물 전달은 업로드 403으로 실패했다. Audit에서 `PutGenericArtifactContentByPath` 거절을 확인했다. 공식 API별 권한 명세의 `GENERIC_ARTIFACT_CREATE`만 해당 파이프라인·구획으로 제한해 임시 허용했다. 삭제·수정 권한을 추가하지 않았다. 함께 확인된 `GetTopic` 거절은 같은 주체·구획의 해당 조회 작업만 허용했다.

프로젝트 준비와 Registry 화면에 도움말을 추가했다.

- 처음 소스 올리기: 커밋한 소스·build_spec.yaml을 Git으로 올리고 같은 브랜치·명세 경로를 선택하는 절차.
- 명세 읽기 권한 오류: 사용자와 파이프라인 권한 구분, Audit에서 요청·주체 확인, 필요한 작업과 구획 범위로 IAM 조건 점검.
- 빌드 성공 후 전달 실패: Artifact Registry 업로드 API의 생성 권한과 지원 조건 변수 확인.

권한 자료: [OCI DevOps IAM Policies](https://docs.oracle.com/en-us/iaas/Content/devops/using/devops_iampolicies.htm). 문서의 광범위한 예시는 검증 정책으로 그대로 적용하지 않았다.

Artifact Registry 자료: [API별 필요 권한](https://docs.oracle.com/en-us/iaas/Content/artifacts/iam-policies-permissions.htm), [지원 조건 변수](https://docs.oracle.com/en-us/iaas/Content/artifacts/iam-policies-supported-variables.htm). 기존 검증용 정책의 저장소별 조건이 모든 API에서 유효하다고 가정했던 부분을 재검토했다. 서비스에서 제공하지 않는 조건 변수는 접근 제한을 보장하는 대안으로 쓰지 않는다.

## 검증 근거

최종 재실행은 빌드·전달 모두 SUCCEEDED였다. 화면에서 Python 테스트 2개 통과, 일반 보고서 다운로드, `test_hello` 빌드 로그와 이번 실행 버전의 애플리케이션·보고서 산출물을 확인했다. 앞선 두 실패 이력은 보존했다.

새 프로젝트의 새 VM 배포 환경·파이프라인으로 이번 산출물을 배포했다. 화면에서 OCI SUCCEEDED와 HTTP `message: hello`를 포함한 배포 로그 42행을 확인했다. 빌드 화면의 배포 연결에서 새 파이프라인의 산출물 SHA-256·크기 일치도 확인했다. HTTP 요청은 검증 VM 내부의 배포 명령으로 수행했으며 외부 공개·무중단 배포 검증은 아니다.

Java 테스트 256개 통과. 실제 데모에서 도움말을 펼쳐 확인했고 390·768·1440px에서 가로 넘침이 없었다. `scripts/browser-build-onboarding.cjs`는 URL·상태 파일·프로젝트 이름을 환경변수로 받아 독립 검증을 재현할 수 있다. 생성 후 ID를 즉시 기록하며 같은 상태 파일 재실행 시 생성 완료 자원을 재조회한다.

개인 OCID·인증·전체 로그는 저장소에 넣지 않는다. 로컬 근거는 `.tools/first-project-state.json`, `first-project-vm-state.json`, `first-project-audit.json`, 브라우저 로그·캡처다. 새 테넌시·새 IAM 사용자·새 VM/네트워크까지 처음 만드는 검증 또는 외부 신규 사용자 사용성 검증은 아니다.
