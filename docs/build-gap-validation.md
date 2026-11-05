# 빌드 누락 보완 검증

2026-10-27 · Redmine [#996](https://redmine.cloud-handson.com/issues/996)

## 반영

- 공통 상단 흐름: 소스 → 빌드 설정 → 저장소 → 실행 → 결과. 현재 위치 강조, 화면/영역 이동. 상세 안내는 접기.
- 실행 화면: 출력별 저장소·경로·버전 조회/수정. 기존 파일 저장소 선택값 유지. 변경은 OCI 전달 설정에 적용.
- 저장 위치 변경: 독립 참조 생성 후 선택한 출력만 연결. 공유 참조와 다른 출력 연결 보존. ETag·범위·일회성 요청 검사.
- Managed Build 생성: 같은 프로젝트의 OCI 저장소 또는 기존 GitHub/GitLab 연결 선택. 브랜치·명세 경로·이미지·제한 시간·선행 단계 입력.
- OCI 소스 생성 검증: 브랜치·명세 조회, 기본 사양 구조 검사. 첫 단계는 파이프라인을 선행자로 지정. 기존 단계 추가는 마지막 단계에만 허용.
- 명세 초안: Maven goal/profile, Gradle task, pytest 옵션, npm script, 작업 경로·파일 출력. YAML 미리보기/다운로드. Git 자동 저장 없음.
- 실행 목적: 테스트만 / 저장 / 저장·배포. 실제 단계와 목적 불일치, 빈 파이프라인, 읽을 수 없는 OCI 명세, 참조 누락·출력 유형 불일치 차단.
- 선택 설명 공란: OCI에 빈 문자열을 보내지 않도록 프로젝트·파이프라인·주제 생성과 파이프라인 수정 보완.

## 검증

`./scripts/build.sh`: 55개 통과. 범위·ETag·중복 요청, 공유 참조 보존, 생성 소스 위조/단계 변경, 목적별 필수 조건, 명세 생성 검증 포함.

실제 DEFAULT / ap-seoul-1 / Joungmin.Ko, `scripts/browser-build-gaps.cjs`:

- 기존 db-companion의 소스 목록·저장소·출력 경로 조회.
- 네 도구의 명세 미리보기.
- 저장 단계가 있는 파이프라인을 테스트 전용으로 제출했을 때 실제 실행 전에 차단.
- 실행/파이프라인 화면 320/390/768/1024/1440/2048px에서 가로 넘침 없음. JavaScript 오류 없음.
- 전용 파이프라인과 Managed Build를 GUI에서 생성. 별도 OCI CLI 조회로 BUILD/ACTIVE, build_spec.yaml, 소스 연결 확인.

검증용 파이프라인: `ocid1.devopsbuildpipeline.oc1.ap-seoul-1.amaaaaaavsea7yiaenzwaxy75rskdtgy2c2k2yccxv4wcynhya632pidvzzq`.
이번 검증에서는 새 빌드를 실행하지 않았다. 기존 db-companion 성공 기록은 [이전 실제 빌드 검증](db-companion-build-validation.md)에 있다.
저장 위치 변경의 독립 참조 생성/연결 보존은 자동 테스트로 검증했다. 이번 브라우저 검증은 기존 목적지 조회까지다.

실제 실패에서 확인한 수정: 공란 description을 전달하면 OCI CreateBuildPipeline이 HTTP 400 / Invalid description을 반환했다.
공란 처리 수정 후 같은 GUI 생성 경로가 성공했다. 실패한 요청에서는 파이프라인이 생성되지 않았음을 목록으로 확인했다.

## 남은 범위

- 외부 연결 신규 생성·Vault 선택, 외부 Git 파일/브랜치 조회. 이번 범위는 기존 OCI Connection 사용까지.
- 명세의 Git 저장·커밋, JDK/런타임 설치 선택, 환경 변수 전용 폼. 이번 도구 입력은 명세 초안이다.
- 로그 활성화 자동 검사/설정, 실행 서비스 IAM 검증, 변수 치환·버전 충돌·전달 단계의 상위 출력 추적.
- Work Request 전용 추적, 생성 결과 불명 조정 기록, 취소·테스트 보고서 집계.
- Wait/Trigger Deployment 생성, VM → Functions → OKE 배포 화면.

준비 상태의 `미확인`은 확인 완료가 아니다. 외부 명세 접근과 서비스 실행 권한은 실제 실행 시 실패할 수 있다.
전체 설계 완료로 판정하지 않으며 #996은 계속 진행 상태다.

## 2026-10-27 후속 보완

환경 변수/Vault 참조 명세 폼, wrapper/system 선택, 실행 취소, 자동 실행 화면과 독립 예약 Function 템플릿을 추가했다. 현재 검증 범위와 미완료 항목은 [자동 실행 검증](design/build-automation.md)에 기록한다. 이전 검증 기록을 전체 기능 완료로 해석하지 않는다.
