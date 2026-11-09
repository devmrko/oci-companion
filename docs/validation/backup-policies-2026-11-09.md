# 정기 백업 정책 검증 — 2026-11-09

백업·복구 → 정기 정책. OCI 제공 정책과 선택 구획의 사용자 정책을 조회하며, 블록 볼륨의 연결·교체·해제를 지원합니다. 앱 자체 예약 실행기는 없습니다. 수동 백업 화면과 정책 조회 권한을 분리했습니다.

## 검증

- DEFAULT / ap-seoul-1 / Joungmin.Ko의 전용 50GB 빈 볼륨 사용.
- 실제 브라우저에서 bronze 연결 → silver 교체 → 연결 해제. 각 실행 뒤 네이티브 조회 결과를 화면에서 확인.
- 확인 화면을 연 뒤 전용 볼륨 태그를 변경하여 ETag 갱신. 이전 확인 화면 실행은 HTTP 409, 정책 연결 없음 유지.
- 390 / 768 / 1440px: 공통 카드·폼·버튼 클래스 사용, 문서 가로 넘침 없음. 좁은 화면의 일정 표는 내부 가로 스크롤.
- Java 테스트 211개 통과. CSRF, 확인 토큰 일회성, 범위 변경, 정책 ETag 변경, 기존 연결 교체, 그룹·교차 리전 차단 포함.
- 브라우저 재검증: Playwright 설치 후 `COMPANION_TEST_PROFILE=DEFAULT node scripts/browser-backup-policies.cjs`. `.tools/backup-policy-live.json`에 별도로 만든 검증 볼륨의 `volume`, `name`을 지정합니다. 이름은 `companion-backup-policy-`로 시작해야 합니다. 기본은 조회만 수행합니다. 연결·교체·해제는 `COMPANION_TEST_MUTATE=backup-policy-validation`을 명시한 경우에만 수행합니다.

## 범위와 제한

- 정책의 주기·유형·시점·시간대·보존 기간·보호 설정을 OCI 응답으로 표시합니다. 원본 정책 자체를 수정하지 않습니다.
- 연결 API는 기존 연결을 교체합니다. 변경 전후를 확인하고 실행 직전에 볼륨·연결·선택 정책 상태를 다시 조회합니다. 연결 API에는 ifMatch나 재시도 토큰이 없어 다른 클라이언트와의 동시 수정까지 원자적으로 막지는 못합니다. 자동 재시도하지 않습니다.
- 해제는 정확한 연결 ID와 ifMatch를 사용합니다. 기존 백업·정책·볼륨을 삭제하지 않습니다.
- 볼륨 그룹 정책, 정책 생성·편집, 교차 리전 복사·KMS 설정은 OCI 콘솔에서 관리합니다. 앱은 IAM을 수정하지 않습니다.
- 예약 시간이 도래한 실제 자동 백업 생성은 이번 검증에 포함하지 않았습니다. 사용자 정의 정책은 대상 구획에 없어 실제 연결 검증은 Oracle 제공 정책으로 수행했습니다.

참고: [OCI 정책 연결](https://docs.oracle.com/en-us/iaas/Content/Block/Tasks/create-bv-volume-backup-policy-assignment.htm), [연결 CLI](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/bv/volume-backup-policy-assignment/create.html), [권한 명세](https://docs.oracle.com/en-us/iaas/Content/Identity/Reference/corepolicyreference.htm).

## 정리

검증용 볼륨은 정책 연결 없음·생성 백업 없음 확인 후 삭제했습니다. OCI `TERMINATED`를 확인했으며 검증 서버 8092도 종료했습니다. 최종 화면은 사용자 앱 8091에 반영하고 실제 조회를 다시 검증했습니다.
