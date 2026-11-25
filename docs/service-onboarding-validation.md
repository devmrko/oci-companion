# 기존 설정을 이용한 새 서비스 등록 검증

`운영 서비스 → 새 서비스 자동 설정`에서 소스에 연결된 기존 설정을 선택하면 현재 Deployment의 볼륨·보안·프로브·pull Secret과 등록 빌더를 읽습니다. 클러스터·Namespace와 새 이름을 확인하고 미리보기에서 생성하면 전용 환경·파이프라인·artifact·stage를 준비하고 배포합니다. 실제 Pod digest와 Ready 확인 후 조회 연결과 운영 서비스 등록을 완료합니다.

2026-11-26 `JOUNGMINKOKOR_API`의 춘천 리전에서 `companion-verify-onboard`를 생성했습니다. 설정 근거는 `sustena-c`였으며 운영 리소스는 읽기만 했습니다. 기존 NodePort 31083을 복제하지 않고 새 ClusterIP Service를 생성했습니다. Secret·ConfigMap은 같은 Namespace의 기존 참조를 사용하며 값은 저장하지 않습니다.

## 실제 결과

- 새 OKE 배포 환경, pipeline, manifest artifact, stage를 화면의 생성 흐름에서 생성했습니다.
- 기존 pipeline과 새 pipeline의 IAM을 자동 점검했으며 새 정책·동적 그룹·보안 규칙을 만들지 않았습니다.
- API 조회는 기존 클러스터의 접속 설정·터널을 재사용했습니다. 별도 kubectl 래퍼는 사용하지 않았습니다.
- 두 검증 Pod가 `d52337647c0c…`를 실행하고 2/2 Ready였으며, 임시 포트 포워딩으로 readyz HTTP 200을 확인했습니다.
- 서비스 상태는 REGISTERED였고, 생성한 조회 연결에서 두 Pod와 ARM64 노드를 확인했습니다.
- 스크린샷: `.tools/verification/screenshots/75-onboarding-preview.png`, `76-onboarding-registered.png`.

환경 생성은 최초 60초 제한을 넘어 `OCI 변경 작업 완료 대기 시간 초과`로 멈췄습니다. 생성한 환경 참조를 보존했으며 ACTIVE 확인 후 **기존 환경에서 계속**으로 재개했습니다. 환경 생성 대기는 180초로 늘렸습니다. 완료 여부가 불확실한 일반 실패에는 자동 재실행을 제공하지 않습니다.

재시작 후 미리보기 재검증에서 JSON 키 순서 차이가 설정 변경으로 오인됐습니다. YAML 문서의 의미를 비교하도록 수정했으며 필드·리소스 버전·등록 정보·IAM 변경은 계속 차단합니다. 수정 후 같은 환경에서 새 파이프라인 생성과 배포가 완료됐습니다.

## 검증 범위와 남은 입력

이번 자동 흐름은 **이미 등록된 소스·서비스를 설정 근거로 새 이름의 서비스를 만드는 경우**입니다. 같은 Namespace에서 기존 파일·인증 참조를 재사용합니다. 설정 근거가 전혀 없는 새 소스, 다른 클러스터나 Namespace로의 이관, 신규 Secret 생성, 새로운 NodePort 외부 노출은 상세 설정이 필요합니다. 임의의 소스만 선택하면 첫 설정이 모두 끝나는 범용 자동화까지 완료한 것은 아닙니다.

전체 `./mvnw verify`는 413개 중 412개 통과, 실제 OCI 전용 테스트 1개 기본 skip입니다. 새 테스트는 clone 대상·selector 분리, 볼륨·프로브·보안·인증 참조 보존, 비밀 환경값·tag 이미지 거부, IAM 부족 차단, 변경 감지, 시간 초과의 재개 범위, 명세 표현 순서 독립 비교를 확인합니다.

## 정리

검증 Deployment·Pod·ClusterIP Service, 전용 DevOps environment·pipeline·stage·artifact를 모두 삭제했습니다. 검증 서비스 등록과 조회 연결·kubeconfig도 해제했습니다. 기존 `sustena-c`와 공유 project·기존 OKE environment·빌더·Secret은 유지했습니다.
