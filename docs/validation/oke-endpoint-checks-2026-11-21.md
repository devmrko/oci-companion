# OKE 접속 점검 검증

2026-11-21. DEFAULT / ap-seoul-1 / Joungmin.Ko. 실제 OCI와 8092 브라우저에서 검증했다.

## 결과

| 경로 | 화면 결과 |
|---|---|
| github.com / | DNS 조회, TLS 신뢰·호스트명·유효기간 확인, HEAD 200 |
| gittea.cloud-handson.com / | DNS 조회, TLS 확인, HEAD 200 |
| expired.badssl.com / | TLS 실패, HTTP 요청 미수행 |
| wrong.host.badssl.com / | TLS 실패, HTTP 요청 미수행 |
| companion-check.invalid / | DNS 미확인, 연결 미수행 |
| 127.0.0.1.nip.io / | DNS 127.0.0.1 확인 후 사설·예약 주소 연결 차단 |

기존 클러스터에는 Ingress가 없었다. 전용 Namespace `companion-endpoint-check-20261121`의 미관리 클래스 Ingress에 검증 경로를 등록했다. 실제 Kubernetes 목록 선택과 공개 서버 접속을 확인한 것이며, OKE 앱을 외부에 배포해 응답을 검증한 것은 아니다. Ingress 주소 미할당 경고가 모든 결과에 표시됐다. 신규 컨트롤러·LoadBalancer·업무 앱은 만들지 않았다.

## 자동 검증

전체 자동 테스트 302개 통과.

공개/사설/예약/전환 주소 구분, 요청 경로·호스트 검사, 상태 줄 길이 제한, 본문 미수집, 현재 Ingress의 정확한 경로만 허용, 조회 실패 구분, CSRF·현재 범위 검증을 포함한다.

## 범위

- 앱 서버에서 공개 HTTPS 443에 HEAD 요청. DNS 첫 주소 하나만 점검한다.
- DNS 결과를 검증한 뒤 해당 IP로 연결한다. 재해석·리다이렉트·인증정보 전달·응답 본문 수집은 하지 않는다.
- JDK 기본 신뢰 저장소, HTTPS 호스트 검증, 인증서 유효기간 확인. 30일 미만은 주의. 인증서 폐기 상태는 별도다.
- TCP/TLS/HTTP 전체 연결은 10초, DNS는 3초 제한. 동시 접속 점검 4개, DNS 작업 2개로 제한한다. OCI/Kubernetes 조회 시간은 별도다.
- 사설/예약 주소와 일부 IPv6 특수 대역은 미지원. 내부 서비스는 VCN 안에서 별도 검증한다.
- DNS와 Ingress IP가 같아도 응답한 앱 버전·기능 정상은 보장하지 않는다. CDN·별칭은 별도 확인한다.

## 정리

검증 Namespace 삭제 후 NotFound를 확인했다. 임시 kubeconfig도 삭제했다.
