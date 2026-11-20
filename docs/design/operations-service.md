# UI·자연어 도구 공통 운영 서비스

2026-11-21. 사용자가 Java 서비스 인터페이스를 통해 화면과 향후 LLM 도구를 연결하도록 요청했다. OCI·Kubernetes 기능을 우선 사용하고, 앱은 조회·설정·실행 연결을 제공한다.

## 경계

`화면 / 향후 LLM 도구 → Java 운영 서비스 → 기존 port → OCI SDK / Kubernetes`

Foundation의 `UseCase<I,O>`는 인증된 입력·DTO 결과·서비스 경계 검증을 정의한다. 이 프로젝트는 기존 기능별 port를 재사용한다. 범용 도구 프레임워크나 별도 실행 엔진은 추가하지 않는다. 클라우드 호출을 DB 트랜잭션으로 묶지 않는다.

첫 구현 `DeploymentOperations`:

| 작업 | 결과 | 변경 |
| --- | --- | --- |
| pipelines / history / status / logs | 네이티브 조회 결과 | 없음 |
| sourceImages | 선택 빌드의 기록된 이미지 | 없음 |
| inspect | 조회 시각·대상·판단 코드·근거·필수 조건 | 없음 |
| prepare | 검증 결과·계획 ID·만료 시각 | 서버 내 임시 계획만 |
| execute | OCI 실행 ID·대상 | 배포 요청 |

`Context`는 신뢰된 어댑터가 인증 세션에서 만든다. LLM 인자로 프로파일·사용자·테넌시·interaction을 받지 않는다. 리전·구획은 사용자가 선택한 범위로 제한한다. 하위 어댑터는 매번 네이티브 인증·리소스 범위를 검사한다.

계획은 주체·리전·구획·interaction에 묶이고 10분 후 만료한다. 실행은 계획 ID만 받는다. 실행 직전에 원본 빌드·명세·이미지·아키텍처를 재조회한다. 변경된 계획, 다른 세션, 임의 ID, 재사용은 실행하지 않는다. 현재 저장소는 프로세스 메모리이며 재시작하면 계획을 다시 확인해야 한다. 세션별 32개, 전체 256개로 제한한다.

조회 결과의 `UNKNOWN`은 성공·실행 권한을 뜻하지 않는다. `executable`은 현재 구현의 차단 조건을 통과했다는 뜻이며 앱 정상 응답·무중단·권한 전체를 보증하지 않는다. 실행 실패로 응답이 불확실하면 새 요청 전에 OCI 이력을 확인한다. 실행 ID로 상태·로그를 다시 조회한다.

## LLM 연결 원칙

- LLM은 상태 설명과 구조화된 작업 입력을 만든다. 권한 판단·명세 일치·실행 여부 검증은 서비스가 한다.
- 조회와 변경 도구를 분리한다. 변경 승인은 호출 어댑터의 인증된 정책에서 결정하고 계획에 연결한다.
- 비밀값·원문 로그를 기본 도구 결과로 넘기지 않는다. 로그는 별도 조회·권한·필터·크기 제한이 필요하다.
- 명령 문자열이나 임의 URL 실행을 일반 도구로 노출하지 않는다.
- 이번 변경은 LLM/MCP 서버를 추가하지 않는다. 실제 자연어 실행은 후속 어댑터와 승인 정책 검증이 필요하다.

## OKE 운영 준비 진행 범위

기존 `OkeDeploymentSetup` 인터페이스에 ConfigMap·Secret·ServiceAccount 이름 참조, startup/liveness 검사, 종료 대기를 추가했다. 표준 YAML이므로 앱 없이 사용할 수 있다. Secret 본문은 읽거나 저장하지 않는다. 2026-11-21 후속 변경: `OkeRuntimeResources`로 같은 Namespace의 기존 리소스를 조회하고 `OkeRuntimePreparation`으로 존재·유형·Ingress backend/TLS 참조를 점검한다. UI는 목록에서 선택하며 미리보기와 저장에서 재조회한다. 실행 권한·Workload Identity·인증서 유효성·실제 HTTPS 응답은 검증 범위 밖이다.

후속 순서:

1. 네이티브 리소스 목록에서 설정·Secret·ServiceAccount 선택, 기존 Ingress/LB·TLS 연결 점검.
2. Workload Identity·Vault 연결 안내와 참조 검증.
3. requests/limits 분리, HPA·PDB·노드 분산 설정과 점검.
4. OCI Helm·환경별 값·승인·배포 후 검사 연결.
5. 에이전트 인증·도구·메모리·관측·평가 서비스 연결.

각 단계는 기존 조회·생성 인터페이스를 재사용하고 서비스에 검사·계획·실행을 둔다. 기존 빌드·백업·환경 생성의 모든 컨트롤러를 옮긴 상태는 아니다. `DeploymentOperations`는 일반 배포 실행 경로에 적용됐으며 별도 복구·구성 생성 경로는 아직 기존 검증을 사용한다.

### OKE 리소스 조회와 참조 점검

- `OkeRuntimeResources`: OCI 배포 환경/클러스터 범위를 검증한 뒤 Kubernetes 목록을 조회한다. ConfigMap·Secret·ServiceAccount는 kubectl 출력 필드를 이름·유형으로 제한한다. Secret 값은 Java DTO나 파일에 담지 않는다.
- `OkeRuntimePreparation`: PASS/FAIL/UNKNOWN/WARN, 조회 시각과 항목별 사유를 반환한다. 화면과 향후 자연어 도구가 같은 인터페이스를 사용한다.
- 조회는 명시적 버튼으로 실행한다. Namespace/API 접속 경로 변경 시 이전 선택을 지운다. 부분 조회 실패는 빈 목록과 구분한다.
- 선택한 참조가 없거나 조회 불가이면 저장을 차단한다. 저장 직전 다시 조회한다. 저장 후 리소스 변경까지 보장하지 않는다.
- Ingress는 기존 설정의 backend와 생성 예정 ClusterIP Service 이름/포트를 비교한다. TLS는 호스트와 Secret 유형을 확인한다. DNS·인증서 내용·HTTPS 응답·컨트롤러 동작은 별도다.
- 기존 LoadBalancer Service와 이름이 충돌하면 Ingress 연결 구성 저장을 차단한다. Ingress/LB/DNS/인증서는 생성하지 않는다. Gateway API는 후속 범위다.

### 실제 공개 HTTPS 점검 (2026-11-21)

`OkeEndpointChecks`는 현재 인증 주체·리전·구획으로 Ingress를 재조회하고 등록된 정확한 호스트/경로만 점검한다. 화면과 향후 자연어 도구가 같은 인터페이스를 사용한다. 결과에는 조회 시각, 대상, DNS·Ingress 주소·TLS·HTTP별 상태와 근거가 포함된다. 참조 점검이나 구성 저장과는 별도 수동 동작이다.

기존 `MonitoringTransport`는 로컬 에이전트용이다. `spring-foundation`의 `FoundationHttpClients`도 검토했으며 신뢰된 고정 origin의 HTTP 서비스 프록시용이다. 이번 Ingress 점검의 DNS IP 고정·인증서 정보 조회는 제공하지 않아 JDK `SSLSocket`으로 OKE 어댑터 안에 구현했다. 공통 HTTP 프록시를 중복 구현하거나 Foundation 의존성을 추가하지 않았다.

근거: [JDK SSLParameters](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLParameters.html), [JSSE](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html). 실제 검증과 제한은 `docs/validation/oke-endpoint-checks-2026-11-21.md`에 기록했다.
