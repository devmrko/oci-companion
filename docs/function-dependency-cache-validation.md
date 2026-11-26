# 의존성 변경 자동화 — 실검증 전

잠금 파일이 바뀌면 Function 안에서 새 의존성 레이어와 런타임 이미지를 만들고, Companion이 해당 빌더 Function을 갱신한 뒤 같은 빌드를 한 번 이어가는 코드를 구현했습니다. **실제 잠금 파일 변경 빌드 성공은 아직 확인하지 못했습니다.** 운영 Function과 운영 앱에는 이 변경을 적용하지 않았습니다.

구현은 [설계](design/function-dependency-cache.md)를 따릅니다. Java 전체 검증은 414개 중 413개 통과, 실제 OCI 전용 1개 기본 skip이며 Python 테스트 9개가 통과했습니다. 이 결과는 실제 Functions 런타임에서 새 의존성을 실행했다는 증거가 아닙니다.

## 실제 준비 시도와 실패

2026-11-26 별도 private OCIR 저장소 `companion/verify-lock-builder`, `companion/verify-lock-output`을 생성했습니다. 기존 ARM Function 앱에 검증 Function을 생성하기 전에 빌더 코드 레이어를 기존 런타임 위에 붙이는 준비를 시도했습니다. `crane append`가 300초 제한을 초과했습니다.

오류 원문(주소·임시 경로 생략): `subprocess.TimeoutExpired: Command '[crane, append, -b, <existing-runtime@digest>, -f, <code.tar>, -t, <verification-runtime-tag>]' timed out after 300 seconds`.

새 seed digest와 Function ID는 기록되지 않았으며, Function 생성 단계까지 도달하지 않았습니다. 전체 원문은 로컬 `/tmp/ridley-cache-bootstrap.log`, 생성 참조는 `.tools/dependency-cache/resources.json`에 있습니다. 이 파일의 전체 OCID는 문서에 옮기지 않습니다.

임시 로컬 uv 설치와 프로세스 진단에는 자동 승인 검토의 `guardian review completed without an assessment payload` 오류가 발생했습니다. bootstrap 중단 요청에도 `The automatic permission approval review did not finish before its deadline`이 발생했습니다. 이는 안전성 거부 판정이 아니며 해당 명령은 실행되지 않았습니다. 조정자의 지시에 따라 작은 정리 명령으로 재시도했습니다.

## 정리 결과와 남은 검증

bootstrap PID 검색 결과 실행 중인 해당 프로세스가 없었고 로그에서 위 시간 초과 종료를 확인했습니다. 이번 작업의 로컬 Companion을 종료하고 두 검증 OCIR 저장소를 각각 삭제 완료했습니다. 기존 Function 앱·운영 빌더·운영 이미지·IAM·보안 규칙·Secret은 변경하지 않았습니다.

남은 검증은 실제 의존성을 추가한 검증 커밋으로 Function 내부 레이어 생성, 자동 런타임 갱신 후 테스트·이미지 push 성공, 동일 잠금 파일의 캐시 재사용을 확인하는 것입니다. 로컬 uv 설치에 의존하지 않고 Function에 이미 있는 uv로 검증해야 합니다. 현재 코드는 실검증 전 상태로 별도 표시하여 커밋합니다.
