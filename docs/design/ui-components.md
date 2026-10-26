# UI 컴포넌트 스타일

공통 스타일은 `app.css`의 클래스로 정의하고 템플릿에서 사용한다.
레이아웃을 전역 `header`, `nav`, `input`, `button` 선택자로 지정하지 않는다.
요소 ID는 동작 연결에 사용하고 시각 스타일은 클래스로 지정한다.

| 컴포넌트 | 클래스 |
|---|---|
| 버튼 | `ui-button`, `ui-button--inverse` |
| 입력·선택 | `ui-control`, `ui-control--compact` |
| 필드 | `form-field`, `form-field--compact`, `form-field--inline` |
| 폼 도구 모음 | `form-toolbar` |
| 표 | `data-table`, `table-heading`, `table-cell`, `table-scroll` |
| 코드 | `code-block` |
| 본문·푸터 | `page-content`, `app-content`, `page-footer`, `app-footer` |
| 네비게이션 | `app-topbar`, `side-nav`, `nav-toggle`, `connection-summary` |

상단은 Grid로 배치한다. 1100px 이하에서 선택 영역을 별도 행으로 분리하고,
440px 이하에서는 적용 버튼을 별도 행으로 둔다.
모바일 메뉴는 세로 목록이며 실제 상단 높이를 기준으로 그 아래에 배치한다.
넓은 표는 `table-scroll` 내부에서 스크롤하고 페이지 전체를 밀어내지 않는다.

검증: `COMPANION_TEST_PROFILE=DEFAULT node scripts/browser-layout-smoke.cjs`.
실제 OCI 읽기 요청이 발생한다. 리소스·소스·빌드·도움말을
320/390/768/1024/1440/2048px에서 확인한다. 스크린샷은 `.tools`에만 저장한다.

헤더와 로고는 `--chrome-height`를 공유한다. 상단 선택 영역의 최대 너비는620px,
일반 필터의 기준 너비는240px이며 작은 화면에서는 줄바꿈한다.
상단 리전·구획은 `form-field--inline`으로 라벨을 선택 상자 왼쪽에 둔다.
데스크톱 헤더 높이는60px이며, 좁은 화면에서도 각 라벨과 선택 상자는 같은 줄을 유지한다.
