# OKE 구성·복구·알파 점검

2026-11-09. DEFAULT / ap-seoul-1 / Joungmin.Ko에서 실제 OCI를 조회했다.

## 구현

- 기존 OKE 배포 환경과 OCIR 저장소·이미지 선택. digest 고정.
- Namespace·앱 이름·amd64/arm64·복제 수·CPU/memory·HTTP readiness·일반 환경변수·기존 imagePullSecret 설정.
- 표준 Namespace/Deployment/ClusterIP Service YAML 생성·다운로드. 앱 없이 Kubernetes에서 사용할 수 있다.
- 기존 PVC 연결: 복제 수 1과 Recreate. PVC 생성·삭제·백업·복원은 수행하지 않는다.
- OCI 파이프라인·인라인 명세·OKE 단계 생성. 자동 롤백은 OCI 기능이며 첫 배포에는 이전 성공 버전이 없다.
- 성공한 실행에서 이전 단계 스냅샷으로 복구: CreateSingleDeployStageRedeploymentDetails. 같은 파이프라인·환경·성공한 단계인지 검사하고 실행 전 다시 검증한다.
- 설정·복구 요청에 범위별 일회용 티켓·CSRF. 결과 불명확 시 자동 재시도 없음. 오류 화면에 메뉴와 이력 이동 유지.

## 실제 검증

- 화면에서 `companion/hello-python` 이미지를 선택하고 YAML 다운로드 및 DevOps 구성 저장을 수행했다. 검증 전용 Namespace는 `companion-oke-validation`이며 기존 앱과 다르다.
- 첫 생성 YAML의 반복 객체가 별칭으로 출력되어 기존 배포 검사에서 거부됐다. 별칭을 없앴고 생성 YAML을 실제 OkeManifests 검사에 넣는 회귀 테스트를 추가했다.
- 수정한 구성 `companion-oke-wizard-v2-20261109` 저장 성공. 첫 화면 실행은 OCI 요청 오류로 끝나 생성 이력이 없음을 독립 조회했다. 진단용 SDK 실행은 접수됐으나 단계에서 권한 부족으로 실패했다.
- OCI 로그: 새 DevOps 배포 파이프라인의 리소스 주체에 OCI 리소스 접근 권한이 필요하다. 기존 파이프라인 성공은 신규 파이프라인 권한을 입증하지 않는다. 권한을 확대하거나 기존 동적 그룹을 변경하지 않았다.
- 새 앱 HTTP 성공, 실제 버전 교체·롤백, PVC 데이터 유지·복원은 미검증이다. 구성 저장/API 접수를 성공 배포로 기록하지 않는다.
- 테스트 196개 통과. 명세 생성→실행 검사, 설정값 전달, 네이티브 생성 요청·부분 실패, 복구 참조, 범위 변경·CSRF·중복 제출을 검사했다.

증거: `.tools/oke-wizard-manifest.yaml`, `oke-wizard-created.json`, `oke-wizard-run-result.json`, `oke-wizard-form.png`, `oke-wizard-preview.png`.

## 빈 데이터와 후속 화면 검증

`.tools/alpha-onboarding-data-20261109`에서 시작했다. 실제 DEFAULT 연결·서울/Joungmin.Ko 선택·db-companion 소스 등록·기존 OKE 성공 실행의 복구 확인 화면·취소를 수행했다. 이후 검증용 새 OKE 파이프라인을 화면에서 실행했고 요청 접수와 실패 로그를 확인했다. 실패 원인은 새 파이프라인의 OCI 리소스 접근 권한이며 실제 HTTP 성공은 아니다. 390/768/1440px 복구 화면 넘침·JS 오류 없음. 사람이 처음 사용하는 사용성 시험을 대신하지 않는다.

증거: `.tools/alpha-onboarding-result.json`, `.tools/alpha-rollback-preview.png`. 로컬 알파 ZIP은 JAR·실행 스크립트·README·검증 범위만 포함하며 공개 업로드하지 않았다.

알파 ZIP을 별도 폴더에 풀고 개발 환경변수를 제거한 채 JAVA_HOME만 지정해 8093에서 기동했다. 실제 프로파일 연결·구획 선택·Container 조회를 브라우저로 확인했다. JDK 미설치/경로 미설정 안내도 보완했다. 배포 묶음에 개인 설정 파일이 없음을 검사했다.
