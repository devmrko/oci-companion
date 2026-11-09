# 빌드 로그 조회 실패 검증 — 2026-11-09

- 환경: DEFAULT / ap-seoul-1 / Joungmin.Ko, 로컬 8091.
- 대상: 기존 성공 실행 `hello-agent-010-image`. 새 빌드는 실행하지 않았다.
- 실제 OCI Logging 로그 183건 조회 후 검색 불일치 시 표시 0건 / 조회 183건 확인.
- Playwright 브라우저만 오프라인으로 전환: 이전 로그 유지, 마지막 성공 시각 표시, 자동 갱신 선택값 끔, 이전 로그 버튼 비활성화 확인.
- 브라우저 연결 복구 후 수동 재조회 성공.
- 390 / 768 / 1440px 가로 넘침 없음. 모바일 캡처 육안 확인.
- Java 테스트 219개 성공. JS 구문 검사 성공.

재현: `COMPANION_TEST_PROFILE=DEFAULT COMPANION_LOG_TARGET=<JSON 경로> node scripts/browser-build-log-failure.cjs`
대상 JSON에는 기존 실행의 `pipeline`, `run` OCID를 지정한다. Playwright는 `.tools/browser/node_modules`에 설치되어 있어야 한다.

이번 검증은 실제 로그 조회와 브라우저 네트워크 단절을 대상으로 했다. OCI IAM 거부·Logging 미설정 상태를 실제 리소스 변경으로 재현하지는 않았다.
