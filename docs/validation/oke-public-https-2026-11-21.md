# 실제 OKE 앱의 공개 HTTPS 검증

2026-11-21. DEFAULT / ap-seoul-1 / Joungmin.Ko의 기존 OKE 클러스터에서 검증했다. 배포 파이프라인 이름은 `companion-https-20261121`이다.

## 확인 결과

| 단계 | 확인 방법 | 결과 |
|---|---|---|
| 배포 구성 | Companion 화면에서 이미지 digest·Namespace·사양·pull Secret 선택 | OCI 파이프라인·명세·단계 생성 |
| 실제 배포 | 화면의 확인한 배포 실행 | SUCCEEDED; 명세 적용·상태 확인 단계 모두 성공 |
| 앱 상태 | 등록 앱 상태 화면 | Pod Running / Ready, 재시작 0 |
| 배포본 | kubectl containerStatuses와 선택 이미지 대조 | digest 일치 |
| 외부 접속 | 실제 HTTPS 주소를 브라우저에서 열기 | GET 200, service=oci-companion-container-demo, architecture=x86_64, status=ok |
| 앱 로그 | Companion Pod 로그 화면 | GET /hello 200 확인 |
| 접속 점검 | 실제 Ingress를 목록에서 선택하고 점검 | DNS와 Ingress IP 일치, TLS 신뢰·호스트명·유효기간 확인 |
| HEAD 응답 | 동일 화면 | 501; 샘플 앱의 HEAD 미지원. GET 정상과 구분해 안내 |

기존 성공 빌드의 고정 digest를 사용했다. 이번 검증에서 새 이미지를 빌드하지 않았다.

## 전용 검증 구성

- Namespace: `companion-https-20261121`. 기존 업무 리소스는 변경하지 않았다.
- 앱: 1 Pod, 250m CPU / 256Mi 메모리, ClusterIP Service, readiness `/hello`.
- Ingress: Traefik v3.7.19, 전용 Namespace만 감시, 이름이 지정된 기존 클래스 annotation 사용. namespace Role/RoleBinding만 부여했다.
- 컨트롤러: CPU 요청 100m / 상한 500m, 메모리 요청 128Mi / 상한 256Mi.
- 네트워크: 기존 공개 서비스 LB 서브넷, OCI Flexible Load Balancer 10–10 Mbps, TCP 80/443.
- DNS/인증서: IP 기반 검증용 sslip.io 주소, ACME HTTP-01 공개 인증서. 기존 사용자 도메인 DNS는 변경하지 않았다.
- DevOps 권한: 해당 파이프라인·클러스터와 프로젝트에 한정한 임시 정책. 검증 후 삭제한다.

검증용 단일 컨트롤러이며 인증서 저장은 emptyDir이다. 운영 환경의 HA·영속 인증서 저장·갱신·도메인 운영 검증은 아니다. Secret 값과 인증서 개인키를 로그나 문서에 저장하지 않았다.

## 발견 및 수정

- OKE 노드가 짧은 이미지 이름을 거부했다. 컨트롤러 이미지를 `docker.io/library/traefik:v3.7.19`로 명시한 후 정상 실행됐다.
- 클래스가 annotation에 있는 Ingress도 화면에 클래스 이름을 표시하도록 보완했다.
- HEAD 501은 서비스 장애로 단정하지 않고 HEAD 미지원과 브라우저 GET 확인을 안내한다. 자동 GET 재시도나 본문 수집은 추가하지 않았다.
- 자동 테스트 303개 통과. 수정한 화면에서도 실제 Ingress 클래스·인증서·HEAD 501 안내를 확인했다.

## 참고

[OCI LoadBalancer 구성](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingloadbalancers-subtopic.htm), [Traefik Kubernetes Ingress 설정](https://doc.traefik.io/traefik/reference/install-configuration/providers/kubernetes/kubernetes-ingress/).

## 정리

Kubernetes Service 삭제 후 실제 OCI Load Balancer 삭제를 확인했다. Namespace의 NotFound, 임시 IAM 정책 삭제, 로컬 조회 연결 해제, kubeconfig 삭제까지 확인했다. 컨트롤러 Pod의 임시 인증서 저장소도 Namespace와 함께 제거됐다. 검증용 HTTPS 주소는 더 이상 서비스하지 않는다. OCI 파이프라인과 성공 실행 이력은 검증 근거로 남겼다.
