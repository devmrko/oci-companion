# Sustena OKE 배포에서 확인한 Companion 기능 공백

2026-11-25. new-sustena(Gitea `joungmin/new-sustena`)를 joungminkokor 테넌시 OKE `oke-shared-a1`(ap-chuncheon-1, ARM64 A1 노드 2대) `personal-services`에 배포하고 https://sustena.store 로 공개했다. 오너 원칙은 "배포는 oci-companion을 가능한 한 사용하고, 안 되는 단계는 CLI로 임시 진행한 뒤 공백을 기록한다"였다.

이 문서는 Companion 개발자가 다음 보강 순서를 정할 때 쓴다. 실제 배포에서 Companion을 쓰지 못한 단계, 그 원인, 대신 쓴 방법, 보강 제안을 단계별로 적는다. 기능 보강 구현은 이번 범위가 아니다.

## 결론

이번 배포에서는 **Companion을 한 단계도 사용하지 못했다.** 가장 큰 원인은 인증이다. joungminkokor 테넌시의 CLI 프로필 `JOUNGMINKOKOR`는 브라우저 로그인으로 만든 security_token 세션인데, Companion은 API 서명 키 프로필만 읽는다. 인증 문제가 풀려도 아래 표의 단계별 공백이 남는다.

우선순위 제안:

1. security_token(세션) 프로필 지원 — 이게 없으면 이 테넌시의 어떤 화면도 쓸 수 없다.
2. 기존 kubeconfig(사설 API 터널) 기반의 상태·로그 조회 — 배포 경로와 무관하게 운영 확인에 바로 쓸 수 있다.
3. 준비된 매니페스트(kustomize) 적용과 1회성 Job 실행.
4. ARM 이미지 빌드(Managed Build의 ARM shape 또는 기존 ARM VM 빌더).

## 단계별 공백

| 단계 | 이번에 쓴 방법 | Companion에 없던 것 | 보강 제안 |
|---|---|---|---|
| 인증 | OCI CLI `--auth security_token`, `oci session authenticate`로 재로그인 | `ConfigFileAuthenticationDetailsProvider`만 사용(`OciCloud`, `OciResourceCatalog`, `OciBuildHistory`). `user`가 없는 세션 프로필은 연결 단계에서 실패. 화면 안내도 "보안 토큰은 후속"(`OciGuides`) | `security_token_file`이 있는 프로필을 `SessionTokenAuthenticationDetailsProvider`로 연결. `Connection.user`가 비는 경우의 범위 키(테넌시+프로필) 정의, 세션 만료 시각 표시와 재로그인 명령 안내 |
| ① ADB 스키마 | new-sustena `scripts/provision_oracle.py`(ADMIN) | ADB 사용자 생성 기능 없음 | Companion 범위로 보지 않음. DB Companion 쪽 기능 후보로만 기록 |
| ③ Secret | `kubectl create secret`(지갑 파일), 표준 입력으로 만든 JSON을 `kubectl apply`(값 출력 없음) | 기존 Secret 선택·참조 검사만 있음(`oke-runtime-resources` 검증). 로컬 파일·.env에서 Secret을 만드는 기능 없음 | 로컬 파일 경로를 받아 값은 표시하지 않고 Opaque Secret을 생성·갱신하는 기능. 키 이름만 보여 주는 미리보기 |
| ④ linux/arm64 빌드 | instance-trilium(aarch64)의 rootless podman | 컨테이너 빌드 실검증은 x86 Managed Build. Gitea 등록은 OCI 빌드의 네이티브 소스 연결을 만들지 않음(`build-specification-delivery.md`). ARM 컨테이너 이미지 빌드 경로 없음 | Managed Build ARM shape 지원 여부를 실측한 뒤 명세 템플릿에 아키텍처 선택 추가. 또는 등록된 ARM VM을 빌더로 쓰는 실행기 |
| ⑤ OCIR push | 같은 VM에서 `podman push --digestfile`(기존 Auth Token) | 빌드 전달 단계 경유만 있음. 전달 결과에 digest가 비는 문제도 이미 기록됨(`live-build-oke-2026-11-20.md`) | push 결과 digest를 저장하고 배포 구성에 바로 넘기기 |
| ⑥ 이미지 고정 | kustomization `images:`에 digest 직접 기록·커밋 | 배포 구성은 Companion이 만든 인라인 명세에만 digest를 넣음 | 저장소의 kustomization 이미지 항목을 digest로 갱신하는 커밋 기능(기존 Git 명세 커밋 경로 재사용) |
| ⑦ 배포 적용 | `kubectl apply -k deploy/k8s`(사설 API, SSH 터널 kubeconfig) | OKE 배포는 OCI DevOps 파이프라인 + Companion 생성 명세(Namespace·Deployment·ClusterIP Service)만. kustomize, ConfigMap 명세, NodePort Service 미지원. 기존 NodePort Service가 있으면 덮어쓰기를 거부(`DefaultOkeRuntimePreparation`). DevOps 프로젝트·IAM 준비도 필요 | 저장소의 준비된 kustomize 디렉터리를 렌더링해 종류·namespace·digest를 검사하고 적용하는 경로. 허용 종류에 ConfigMap·NodePort Service 추가 |
| ⑧ init-db Job | `kubectl apply -k deploy/k8s/init-db`, `wait`, `logs` | Job은 Companion 자체 검증 Job(`oke-validation-jobs.md`)만 | 앱 이미지로 1회성 Job을 실행·대기·로그 확인하는 기능(완료 후 TTL 정리) |
| ⑨ 노출·TLS | Trilium VM nginx → NodePort 31082, 워커 보안 목록에 10.55.0.190/32 → 31082 추가(OCI CLI, 변경 전 규칙 백업), certbot webroot | Ingress·LB 경로의 접속 점검만 있음(`oke-public-https`). VM 리버스 프록시·보안 목록 규칙 편집 없음 | 보안 목록 규칙 추가의 미리보기·적용(기존 규칙 보존 확인). DNS·인증서 점검을 Ingress 외 임의 호스트에도 적용 |
| ⑩ DNS | 오너가 등록기관(registrar-servers) 화면에서 변경 | 해당 없음 | 범위 밖 |
| 운영 확인 | `kubectl get/logs`, `curl --resolve`, 브라우저 | 등록 앱 상태·Pod 로그 화면은 API 키 프로필과 OCI kubeconfig 생성이 전제. 사설 API는 server/tlsServerName 설정이 있으나 SSH 터널을 만들지 않음 | 인증 보강 후 기존 kubeconfig 파일을 등록해 상태·로그 화면을 쓰는 경로 검증 |

## 확인 방법과 한계

- 공백 판단 근거: 이 작업공간 커밋 `14142cb`의 코드와 `docs/design`, `docs/validation` 문서. 인증 실패는 코드 경로로 확인했고 Companion 화면을 띄워 재현하지는 않았다.
- ARM Managed Build 지원 여부는 실측하지 않았다. "없음"은 Companion에 검증된 경로가 없다는 뜻이다.
- 이번 배포의 실제 명령과 결과는 new-sustena 커밋 기록과 `~/.local/state/oke-joungminkokor/sustena/`(비공개, Git 제외)에 있다.
