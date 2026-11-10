# OKE 조회 연결·경고 이벤트 검증

2026-11-10. 에이전트 실행 → 등록 앱 상태 → OKE 조회 연결.

## 구현

현재 프로파일·리전·구획의 클러스터를 OCI에서 조회한다. API 접속 경로를 고르고 namespace·Deployment 목록을 Kubernetes에서 읽는다. namespace 목록 권한이 없으면 이름을 직접 입력한다. 저장할 때 Deployment 소속과 UID, ReplicaSet·Pod 조회를 다시 확인한다. 등록과 수정은 로컬 JSON에만 기록하며 IAM·RBAC·클러스터를 변경하지 않는다.

OKE 연결에는 별도 HTTP health 인증이 필요하지 않다. 해당 영역은 HTTP health 미설정으로 표시하고 Kubernetes 결과와 분리한다. 저장 실패 시 폼 입력값을 유지한다. 기존 관리자 kubeconfig 방식은 유지한다.

네이티브 CreateKubeconfig API에서 서버 주소와 CA만 사용한다. 응답의 exec 명령은 실행하지 않고 관리자가 지정한 OCI CLI의 generate-token 명령에 현재 프로파일·config 파일·리전·API 키 인증을 고정한다. 기본 ~/.kube/config는 변경하지 않는다. 생성한 연결 파일은 0600이며 토큰·개인 키를 저장하지 않는다. 프로파일·사용자·테넌시·리전·구획·앱별 바인딩을 분리한다.

현재 Deployment → ReplicaSet → Pod 소유 UID로 경고 이벤트를 필터링한다. 시각·이유·확인할 항목을 최대 20개 표시한다. 원문 메시지는 표시·저장하지 않는다. 이미지 받기, 스케줄링 CPU/메모리 부족, 볼륨 연결, 상태 검사, 재시도 대기를 구분한다. events list 실패는 이벤트 영역에만 표시하며 Pod·노드 결과를 유지한다. 이벤트는 과거 기록일 수 있으며 현재 장애를 확정하지 않는다. 컨테이너 아키텍처 오류는 이 이벤트만으로 단정하지 않는다.

## 실제 검증

- Java 테스트 245개 통과. 범위 변경 거부, native exec 제거·프로파일 고정, 바인딩 격리, 저장 전 Deployment 재검사, 실패 시 초안 유지, 이벤트 소유 UID·원문 비노출·조회 실패 구분 포함.
- 8092 격리 환경에서 DEFAULT/서울의 기존 workload를 화면으로 선택·등록·수정. Deployment 준비 완료, HTTP health 미설정, 실제 amd64 노드 보고값 확인. 390·768·1440px 페이지 넘침 없음.
- 별도 검증 namespace에 존재하지 않는 이미지 태그를 참조하는 소형 Deployment 생성. 실제 이미지 받기 실패 이벤트가 화면에 표시됨. 등록 수정 후 같은 연결 ID 유지.
- 기존 workload·노드·IAM 변경 없음. 검증 namespace와 임시 서버·연결 파일은 검증 후 삭제.

## 실행 조건과 한계

Companion 서버에 OCI CLI와 kubectl이 필요하다. 경로는 companion.oci-cli, companion.kubectl 설정으로 지정한다. 사설 API는 서버에서 직접 접근할 수 있어야 하며 이 화면이 터널을 만들지는 않는다. 공인 API 실제 검증이며 사설 API는 이번 실측 대상이 아니다. 신규 Pod 스케줄링 성공 예측·클러스터 생성·상시 이벤트 보관 기능은 아니다. 클러스터 전체 namespace 조회 권한이 없을 때의 직접 입력 경로를 제공하지만 실제 RBAC를 제거하는 장애 실험은 하지 않았다.

브라우저 재현 스크립트: scripts/browser-oke-connections.cjs. COMPANION_TEST_PROFILE/REGION/COMPARTMENT/CLUSTER/NAMESPACE/DEPLOYMENT와 COMPANION_TEST_WRITE_LOCAL=1을 지정한다. COMPANION_TEST_URL 기본값은 http://127.0.0.1:8092. 실패 예제는 COMPANION_EXPECT_IMAGE_PULL_FAILURE=1을 지정한다. 스크립트는 클라우드 리소스를 만들지 않고 로컬 조회 연결을 등록·수정한다.

## 근거

- [OCI 클러스터 액세스](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengdownloadkubeconfigfile.htm)
- [OCI create-kubeconfig](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/ce/cluster/create-kubeconfig.html)
- [Kubernetes Event API](https://kubernetes.io/docs/reference/kubernetes-api/core/event-v1/)
