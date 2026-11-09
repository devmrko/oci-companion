# Container 실제 실행·수명 주기 검증

2026-11-09, 실제 OCI 서울 구획에서 앱 화면으로 검증했습니다.

- 공식 nginx의 linux/amd64 digest를 고정한 외부 이미지로 생성. 1 OCPU·2GB, 기존 NAT 연결 사설 서브넷, 공용 IP 없음.
- 화면에서 생성값 확인과 네이티브 JSON 다운로드 후 생성 요청. OCI 상태 ACTIVE 확인.
- 화면에서 로그 조회 → 중지(INACTIVE) → 시작(ACTIVE) → 재시작(ACTIVE) 확인.
- OCI HTTP 상태 확인은 HEALTHY. 화면에서 이를 표시하고 nginx 로그를 조회했습니다.
- 화면에서 이름 확인 후 검증 인스턴스 삭제. DELETED까지 확인했습니다.
- 기존 인스턴스와 네트워크 규칙은 변경하지 않았습니다.

## 구현

외부 이미지는 digest 주소를 입력합니다. OCIR 선택과 동시에 사용할 수 없습니다. 외부 사설 Registry는 OCI 네이티브 Vault imagePullSecrets 참조를 선택할 수 있습니다. Secret 목록·상태·버전은 OCI Vault에서 조회하고, 앱은 비밀 내용을 읽거나 저장하지 않습니다. Secret 버전 변경·다른 구획·비활성 Secret은 생성 전 재검증합니다.

외부 Registry의 사설 네트워크 접근에는 NAT 경로를 요구합니다. OCI 서비스 게이트웨이만으로 외부 Registry 접근 가능하다고 판단하지 않습니다. OCIR 사설 이미지는 리소스 주체 IAM 경로를 유지합니다.

## 검증 한계

198개 테스트 통과. 실제 외부 사설 Registry 인증 실행은 미검증입니다. Vault 참조 JSON·구획 검증·Secret 버전 변경은 자동 테스트로 확인했습니다. 이번 공개 이미지 실행 성공은 기존 OCIR 인증 문제 해결을 의미하지 않습니다. OKE·PVC 검증 결과와 별도로 취급합니다.

근거: 브라우저 캡처와 `.tools/container-public-lifecycle.json`, `container-public-status.json`, `container-public-cleanup.json`. 재현 스크립트 `scripts/browser-container-live-lifecycle.cjs`는 전용 검증 인스턴스 정보와 명시적 실행 플래그가 필요합니다.

[OCI Vault 이미지 인증](https://docs.oracle.com/en-us/iaas/Content/container-instances/vault-secrets-image-authorization.htm)
