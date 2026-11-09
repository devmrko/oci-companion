# 보고서 선택 변경 검증 — 2026-11-09

대상: DEFAULT / ap-seoul-1 / Joungmin.Ko의 기존 실패 빌드와 `companion-build-reports-validation` 버킷. 새 빌드·객체 생성 없이 조회만 수행했다.

- 실제 OCI 보고서 목록 응답을 브라우저에서 보류하고 접두어 변경: 이전 응답을 전달해도 목록과 보기 버튼에 적용되지 않음.
- 존재하지 않는 접두어 조회: 보고서 없음 표시, 보기 버튼 비활성화.
- 브라우저 오프라인 조회: 실패 안내, 보기 버튼 비활성화.
- 연결 복구 후 원래 접두어 조회: 보고서 1개 선택 및 JUnit 테스트 1 / 실패 0 / 오류 0 표시.
- 390 / 768 / 1440px 가로 넘침 없음. 390px 화면 육안 확인.
- Java 테스트 219개, JavaScript 구문 검사 통과.

재현: `COMPANION_TEST_PROFILE=DEFAULT COMPANION_REPORT_TARGET=<JSON 경로> node scripts/browser-report-list-selection.cjs`
JSON의 `url`에는 기존 보고서가 있는 빌드 실행 상세 주소를 지정한다. 스크립트는 검증 버킷과 기본 접두어 `build-reports`를 사용한다. Playwright는 `.tools/browser/node_modules` 설치가 필요하다.

응답 내용은 실제 OCI 조회 결과이며 테스트에서 응답 시점만 제어했다. OCI 권한 변경이나 서비스 장애는 발생시키지 않았다. 이번 검증은 Object Storage 보고서 목록 선택 흐름에 한정한다.
