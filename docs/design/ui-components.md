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
| 필드 라벨 높이 정렬 | `build-field-grid form-grid--aligned` |
| 입력창·버튼 묶음 | `form-control-row` |
| 하단 실행 영역 | `form-toolbar form-actions` |
| 체크박스·라벨 | `form-check` |
| 빈 결과 영역 숨김 | `form-result` |
| 영역 간 세로 간격 | `section-stack` |
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
# 빌드 폼 추가 설계 — 2026-10-27

[빌드 화면 설계](build-workflow.md)의 폼은 기존 공통 CSS 클래스를 사용한다. 아래는 구현 예정 규칙이다.

- 필드: 라벨·필수/조건부/선택·기본값 출처·오류를 같은 컴포넌트에 표시.
- 리소스 선택: 검색 목록·새로 조회·생성·상세를 공통 구성으로 제공.
- 실행 준비: 항목·상태·근거·해결 화면 링크. 색상만으로 상태를 구분하지 않음.
- 변경 검토: 현재값·변경값·저장 대상·적용 범위 표시.
- 출력 매핑: 데스크톱 표, 좁은 화면 스크롤 또는 라벨이 유지되는 카드. 중첩 form 금지.
- CSS 클래스는 app.css에 정의. 화면별 inline 스타일과 ID 기반 배치 금지.

상단 흐름은 `build-flow` 공통 fragment와 CSS 클래스로 구현한다. 큰 단계의 상자·화살표와 현재 단계만 표시한다.
`aria-current="step"`, 키보드 포커스, 좁은 화면의 흐름 영역 스크롤을 지원한다. 완료 상태를 추정한 체크 표시는 사용하지 않는다.

2026-11-02 예약 폼은 위 공통 클래스를 조합한다. cron 입력창과 미리보기 버튼을 같은 행에 두고, 결과와 실행 확인은 별도 행으로 분리한다. 라벨 유무 때문에 버튼에 개별 margin/padding을 넣지 않는다. 화면별 ID·인라인 스타일로 위치를 보정하지 않는다.
