# OKE 기존 리소스 선택·참조 검증

2026-11-21, DEFAULT / ap-seoul-1 / Joungmin.Ko. 실제 OKE와 8092 브라우저에서 검증했다.

## 확인

- 전용 Namespace `companion-runtime-check-20261121`에 ConfigMap, 합성 테스트 Secret, ServiceAccount, 테스트 TLS Secret, 미관리 클래스 Ingress를 생성했다. 기존 업무 리소스는 수정하지 않았다.
- 화면의 `Namespace 리소스 조회`에서 실제 목록을 확인했다. 앱 Secret 목록에는 Opaque만 나타났고 TLS Secret은 제외됐다.
- 목록 선택 후 명세 확인: ConfigMap/Secret/ServiceAccount, Ingress → hello:8080, TLS Secret 참조 통과. Ingress 주소 미할당은 별도 확인으로 표시됐다.
- YAML에는 envFrom과 serviceAccountName이 반영됐다. Secret 값은 포함되지 않았다.
- 미리보기 이후 ConfigMap을 삭제하고 화면에서 저장을 눌렀다. 재조회에서 누락을 감지해 OCI 구성 생성을 차단했다.
- 단위 검증: 없는 리소스/조회 불가 구분, 다른 Namespace 거부, 500개 한도, 잘못된 backend 포트/Secret 유형/기존 LoadBalancer 충돌 차단, wildcard 호스트 범위, 저장 재검증.

- Ingress backend 8080과 입력 포트 9090의 불일치를 화면에서 확인했다. 저장 버튼은 비활성화됐고 입력 폼은 유지됐다.
- 전체 자동 테스트 296개 통과.

## 범위

이 검증은 참조 검사다. Pod 실행, 실제 HTTPS, 인증서 유효성, Workload Identity 권한 검증은 포함하지 않는다. 테스트 Ingress는 미관리 클래스로 생성했으며 외부 LoadBalancer를 생성하지 않았다. Kubernetes API 요청 자체는 kubectl의 표준 list를 사용하고, 앱이 받는 Secret 출력은 이름·유형으로 제한한다.

## 정리

검증 Namespace 삭제와 NotFound를 확인했다. 임시 kubeconfig·테스트 인증서·개인키를 삭제했다. 신규 OCI 파이프라인이나 외부 LoadBalancer는 생성하지 않았다.
