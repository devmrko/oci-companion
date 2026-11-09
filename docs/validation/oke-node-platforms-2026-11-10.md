# OKE 노드 플랫폼 표시 검증

2026-11-10. 등록 앱 상태 → 앱이 사용하는 OKE 노드에서 CPU·OS 보고값과 라벨을 비교한다.

- status.nodeInfo의 architecture/operatingSystem과 kubernetes.io/arch·os 라벨을 별도로 표시한다.
- 알려진 값이 다르면 불일치, 누락되면 미확인이다. 라벨로 보고값을 추정하지 않는다.
- 기존 Deployment 소유 Pod에 할당된 노드만 읽는다. inspectNodes 설정과 nodes get 권한이 필요하다.

## 검증

Maven 테스트 232개 통과. 플랫폼 불일치·일치·누락·잘못된 값과 기존 노드 조회 범위 제한을 확인했다.

DEFAULT 프로파일의 서울 구획에서 기존 workload를 읽기 전용으로 연결해 실제 브라우저로 조회했다. 할당 노드는 amd64 / amd64, linux / linux, 플랫폼 라벨 일치, Ready=True였다. NetworkUnavailable 조건은 Unknown으로 표시됐다. 390·768·1440px에서 페이지 넘침과 JavaScript 오류가 없었다.

8092의 별도 로컬 관리자 설정으로 검증했다. 앱 자체 HTTP health 인증은 구성하지 않았으며, 이번 확인 대상은 Kubernetes 조회 영역이다. 기존 workload·노드·IAM은 변경하지 않았다. 검증용 서버와 임시 kubeconfig는 검증 후 정리한다.

재현: Playwright 설치 후 scripts/browser-oke-node-platforms.cjs를 실행한다. COMPANION_TEST_PROFILE, COMPANION_TEST_APP, COMPANION_TEST_REGION, COMPANION_TEST_COMPARTMENT를 명시한다. COMPANION_TEST_URL은 기본 http://127.0.0.1:8092, COMPANION_TEST_ARCH는 기본 amd64다. 이미 구성된 읽기 전용 앱 연결을 사용한다.

이 결과는 이미지 호환성이나 새 Pod 스케줄링 성공 검증이 아니다. 노드 조회 연결은 관리자 파일 설정이며 자동 등록 UI는 포함하지 않는다.

## 근거

- [Kubernetes Node API](https://kubernetes.io/docs/reference/kubernetes-api/core/node-v1/): nodeInfo 보고값
- [Node labels](https://kubernetes.io/docs/reference/node/node-labels/): 노드 라벨과 신뢰 한계
