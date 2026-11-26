# 배포 점검에서 필요한 조치로 이어가기

배포가 막힌 이유와 다음 행동을 같은 화면에서 확인합니다. 기존 점검·미리보기·확인 실행 경로를 재사용하고, 목록에서 필요한 항목만 열어 근거와 조치를 확인합니다.

## 완료 조건

- 공통 `Readiness`에 점검 상태·권한 근거·조치·관리자 안내를 둡니다. 통과, 확인 후 처리, 권한 안내로 묶되 권한 미확인을 권한 없음으로 표현하지 않습니다.
- IAM·보안 목록 조회 성공만으로 생성·수정 권한을 인정하지 않습니다. 실제 사용자 그룹·정책 조회에서 확인한 제한된 무조건부 허용 문장 또는 실제 API 오류를 근거로 사용합니다. 조건부·해석 불가·조회 실패는 미확인입니다. Kubernetes 권한은 self-subject authorization 조회 결과로 판단합니다.
- 생성 버튼은 범위·사용자·유효기간에 묶인 미리보기와 명시적 확인을 요구합니다. 실행 직전 범위·권한·리소스 변경을 재점검합니다. 실제 OCI/Kubernetes 오류는 민감값을 가린 뒤 표시합니다.
- NodePort 포트 충돌과 Service 생성 권한을 확인합니다. Service는 새 이름으로만 생성합니다. 보안 규칙은 기존 규칙을 유지하며 미리보기까지 검증하고 실제 추가는 하지 않습니다. 외부 프록시에는 예시만 제공합니다.
- 배포 폼은 파일 모드 0440, emptyDir 64Mi, 비루트 UID 10001, RuntimeDefault seccomp, 권한 상승 금지, Service 링크 비활성, 프로브 지연·주기를 기본 적용합니다. 고급 설정에서 수정하며 현재 Deployment를 읽어 폼으로 불러올 수 있습니다.
- 기존 이름은 원인과 등록 서비스 재배포 경로를 표시합니다. kubeconfig 삭제는 실제 부모 경로를 비교하되 파일·데이터 디렉터리 자체의 심볼릭 링크를 통한 이탈은 거부합니다.
- kubectl 미설치/경로 오류와 발견한 실행 파일을 표시하고 확인 버튼으로 공통 실행 경로에 적용합니다. ARM 빌더 등록 여부도 점검 목록에서 확인합니다.

## 검증과 보호 범위

단위·웹 테스트와 `./mvnw verify`, JOUNGMINKOKOR_API의 실제 Companion 화면을 확인합니다. 권한 없음의 실제 재현이 불가능하면 단위/웹 모의 오류와 실제 조회를 구분해 기록합니다. 운영 서비스·NodePort 31080/31081/31083·기존 IAM·보안 규칙·Secret은 변경하지 않습니다. 새 IAM·보안 규칙 생성 버튼은 미리보기까지만 검증합니다. 필요한 검증용 새 리소스는 이름과 범위를 확인하고 정리합니다.

근거: Gitea `devmrko/sustena-deploy`의 `62ecba2`, `docs/backlog/sustena-deploy-gaps.md`. 정책 해석은 [Oracle 정책 문법](https://docs.oracle.com/en-us/iaas/Content/Identity/Concepts/policysyntax.htm)과 [정책 생성·이름 참조](https://docs.oracle.com/en-us/iaas/Content/Identity/policymgmt/managingpolicies_topic-To_create_a_policy.htm)를 참고합니다. 이름은 정책 생성 때 ID로 연결되므로 이름만 맞는다는 이유로 권한을 인정하지 않습니다. 문서 가이드의 지식 DB 원문 조회는 연결 오류로 실패했습니다.
