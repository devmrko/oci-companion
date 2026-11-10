# OKE 사설 API 실제 화면 검증

2026-11-10 15:46 KST 이후. Codex가 DEFAULT 프로파일·서울 리전의 기존 OKE 클러스터에서 독립 namespace로 확인했습니다. 앞선 Bastion 한도·VM 생성 404 때문에 멈췄던 검증을 완료했습니다.

## 연결 경로

앱 서버 → localhost SOCKS5 → SSH 키 인증 → 같은 VCN의 임시 Container Instance → 사설 OKE API. 접속 컨테이너는 1 OCPU·1GB이며 자원 주체 권한을 끄고 SSH 키 인증만 사용했습니다. 전용 NSG는 클라이언트 IPv4 /32의 SSH 입력과 패키지 다운로드·DNS·사설 API 단일 주소의 6443 출력만 허용했습니다. 기존 보안 목록은 변경하지 않았습니다.

서브넷의 기존 출력 규칙이 worker subnet으로 제한돼 패키지 다운로드가 안 됐습니다. 전용 NSG 출력으로 해결했습니다. 처음 SSH 시작 명령의 불필요한 계정 잠금 해제도 제거한 뒤 컨테이너를 재생성했습니다. 최종 컨테이너에서 SSH 리슨·키 인증·사설 API 조회가 성공했습니다.

## 화면 결과

1. 사설 엔드포인트 선택 → namespace 목록 → Deployment 목록 실제 조회.
2. 검증 Deployment hello를 연결 저장 → 준비 완료.
3. 연결 수정 화면에서 PRIVATE_ENDPOINT 선택 유지 확인.
4. Pod·컨테이너 선택 → 로그 조회 완료, 실제 nginx 시작 로그 확인.
5. 저장 kubeconfig의 server가 사설 IP임을 확인. OCI 제공 CA 사용, insecure-skip-tls-verify 없음.
6. SSH 터널 중단 → 같은 연결이 조회 불가. 공인 API로 우회하지 않음.
7. 터널 복원 → 같은 연결이 준비 완료.
8. 도움말 추가 JAR로 교체 후 기존 사설 연결 준비 완료 유지. 연결·로그 도움말을 390·768·1440px에서 확인.

검증은 실제 OCI SDK·kubectl·브라우저로 수행했습니다. API 응답이나 상태·로그를 mocking하지 않았습니다. Java 테스트 259개 통과. 패키지에는 화면과 동일한 JAR를 포함합니다.

## 사용자 접속 방법

앱을 VCN 안에서 실행하거나 기존 VPN·전용망을 사용하면 사설 엔드포인트를 바로 선택할 수 있습니다. 기존 SSH 접속 서버가 있는 경우 다음과 같이 앱 서버에서 실행합니다.

```sh
ssh -N -D 127.0.0.1:10891 SSH_HOST

# 별도 터미널
HTTPS_PROXY=socks5://127.0.0.1:10891 \
NO_PROXY=localhost,127.0.0.1,.oraclecloud.com,.oracle.com \
./run.sh
```

SSH_HOST는 사설 API에 접근 가능한 호스트입니다. SSH 키·known_hosts를 정상 설정하고 인증서 검증을 끄지 않습니다. 이 환경 설정은 하위 HTTPS 도구에도 적용됩니다. 기존 프록시 설정과 병합하며 사설 API 주소를 NO_PROXY에 넣지 않습니다. 터널은 조회하는 동안 유지합니다.

## 검증 범위

같은 VCN에서 사설 API로 연결되는 SSH/SOCKS5 경로를 검증했습니다. 관리형 OCI Bastion, VPN·전용회선 구축, 모든 망 구성의 성공을 의미하지 않습니다. 임시 컨테이너를 운영 표준 접속 서버로 권장하는 문서가 아닙니다. VM LaunchInstance의 404는 권한·NSG·이미지 대조 후에도 남았으나 별도 경로로 사설 조회 목적을 달성했습니다.

기존 업무 Deployment·권한·보안 목록은 변경하지 않았습니다. 외부 신규 사용자 사용성 검증을 대체하지 않습니다.

검증 후 임시 컨테이너 2개(실패 시작본·수정본), 독립 namespace, 전용 NSG, 임시 LaunchInstance 정책, 앱·SSH 터널과 로컬 키·kubeconfig를 정리했습니다. 기존 데모와 업무 자원은 유지했습니다.

최종 alpha.11 ZIP을 빈 데이터 폴더에서 실행해 실제 프로파일 연결과 펼친 사설 API·OCIR 도움말을 390·768·1440px에서 확인했습니다. JavaScript 오류와 가로 넘침이 없었습니다. 최종 ZIP의 JAR는 사설 연결 검증에 사용한 JAR와 SHA-256이 같습니다.
