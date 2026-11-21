# OKE Workload Identity 화면 검증 — 2026-11-21

환경: DEFAULT / ap-seoul-1 / Joungmin.Ko. 기존 Enhanced OKE 클러스터,
격리 Namespace `companion-access-20261121`, 비공개 빈 버킷과 전용 ServiceAccount 사용.
브라우저에서 선택·정책 확인·검증 실행·결과 조회·명세 다운로드·Job 삭제를 수행했다.
준비와 IAM 정책 적용·최종 정리는 OCI SDK/kubectl로 수행했다.

| 검증 | 실제 결과 |
|---|---|
| 정책 적용 전 `app-reader` | HTTP 404, 권한·대상 확인 필요 |
| 화면의 정책을 적용한 직후 | HTTP 404, 아직 권한 미반영 |
| 전파 후 새 Job의 `app-reader` | HTTP 200, 접근 확인 |
| 동일 버킷에 `other-reader` | HTTP 404, 지정 ServiceAccount만 허용 |
| 표준 Job 명세 다운로드 | 브라우저 다운로드 발생 |
| 검증 Job 삭제 버튼 | 삭제 요청 완료, 해당 Job 제거 |

성공 Job: `companion-access-9fabe10d-f21d-4d4d-b7cd-d02622b41b90`.
실제 네이티브 OCI Python SDK의 `ListObjects` 호출 결과다. Job의 종료 상태만으로
접근 성공을 판정하지 않았다. 객체 이름·내용·토큰·원문 예외는 화면에 표시하지 않았다.

화면 검증에서 SDK 클러스터 유형의 `toString()`과 API 값 차이,
리전 OCID의 하이픈 검증 오류를 발견해 수정했다. 결과는 실행 후 상단에 배치하고
공통 상태 표시 클래스를 사용한다.

## 자동 검증

정책의 클러스터·Namespace·ServiceAccount·버킷 제한, 명세의 고정 이미지·실행 제한,
원문 로그 비노출, 잘못된 성공 응답 거부, Kubernetes 라벨 길이,
웹 실행표 재사용·범위 변경 차단을 테스트한다.

## 정리·제한

임시 IAM 정책과 버킷은 삭제 후 404 확인. Namespace 삭제 후 조회 결과 없음 확인.
Job·Pod·ServiceAccount도 Namespace와 함께 제거했다. 임시 kubeconfig 삭제.
기존 앱·노드·네트워크는 변경하지 않았다.

Object Storage **객체 목록 읽기** 검증만 완료했다. Vault, 객체 쓰기, 기존 앱의
SDK·NetworkPolicy 구성, 다른 클러스터·테넌시는 이 결과의 검증 범위에 포함하지 않는다.

원본 로컬 실행 증거: `.tools/workload-access/` (브라우저 결과, 생성·삭제 상태).
