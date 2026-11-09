# Container Instances 운영 작업 검증

2026-11-09. 네이티브 Start/Stop/Restart/Delete API를 화면에 연결했다. OCI 상태가 ACTIVE이면 중지·재시작·삭제, INACTIVE이면 시작·삭제, FAILED이면 삭제를 제공한다. 변경 중이거나 삭제된 리소스에는 작업 버튼을 제공하지 않는다.

작업 확인은 10분 유효한 단일 사용 티켓으로 프로파일·리전·구획·리소스·작업을 묶는다. 실행 시 OCI를 다시 조회하고 ETag 조건부 요청을 보낸다. 삭제는 이름 입력도 검사한다. 중지·재시작 시 임시 데이터 손실과 시작 시 과금 재개를 확인 화면에 표시한다. SDK 응답 실패는 재실행 대신 OCI 상태를 확인하도록 안내한다.

- SDK 요청 테스트: 네 작업의 If-Match 전달, 다른 구획 차단, 변경된 ETag 차단, 허용되지 않는 상태 차단.
- 웹 테스트: 상태별 버튼, 삭제 이름, CSRF, 단일 사용 티켓, 확인 후 구획 변경 차단.
- 실제 DEFAULT / 서울 / Joungmin.Ko 화면: 기존 검증 인스턴스 3개의 DELETED 상태를 조회했고 운영 버튼이 없는 것을 확인했다. 390/768/1440px 넘침·JavaScript 오류 없음. 증거: `.tools/container-lifecycle-ui.json`, `.tools/container-lifecycle-ui.png`.
- 실제 시작·중지·재시작·삭제 요청은 이번 검증에서 실행하지 않았다. 현재 구획의 검증 인스턴스가 모두 삭제 상태이므로 정상 실행 리소스에서의 후속 검증이 필요하다.
- 사설 이미지 인증 문제는 해결되지 않았다. 동적 그룹 100개 중 관련 이름을 가진 그룹을 읽었지만 선택 구획용 Container 규칙을 확인하지 못했다. 기존 그룹·IAM 정책을 변경하지 않았다. 이름 기반 조회이므로 모든 그룹에 적용 가능한 규칙이 없다는 결론은 아니다.

공식 상태·과금: https://docs.oracle.com/en-us/iaas/Content/container-instances/overview-of-container-instances.htm
공식 재시작·임시 데이터 손실: https://docs.oracle.com/en-us/iaas/Content/container-instances/restarting-a-container-instance.htm
