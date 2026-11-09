# 백업 이력 상세 검증 — 2026-11-09

백업·복구의 OCI 백업 이력에 생성 방식(수동·예약), 자동 만료 시각, 보존 보호 종료 시각, 삭제 보호·보존 잠금·법적 보존, 복사 원본 백업을 표시합니다. 별도 이력 DB나 추정 값을 만들지 않습니다.

- 실제 화면에서 전용 빈 블록 볼륨의 전체 백업 생성.
- 백업 행의 `수동`, `자동 만료 없음`, `설정 없음`을 OCI 원본 응답과 대조.
- 실제 백업 행을 펼친 상태에서 390/768/1440px 확인. 표는 기존 공통 클래스를 사용하며 문서 가로 넘침 없음.
- Java 212개 통과. 예약 백업의 만료 시각과 보호 종료 시각 구분, 법적 보존 표시, 알 수 없는 생성 방식에서 무기한으로 추정하지 않는 동작 포함.
- 수동 백업으로 실제 화면을 검증했습니다. 예약 시각에 자동 생성된 백업과 보호 설정된 백업의 실제 생성은 이번 검증 범위가 아닙니다.

근거: [OCI VolumeBackup 명세](https://docs.oracle.com/en-us/iaas/tools/python/latest/api/core/models/oci.core.models.VolumeBackup.html). `expirationTime`은 자동 삭제 예정 시각이며, `timeRetentionExpiresAt`은 보호 기간 종료 시각입니다. `isIndefiniteRetentionEnabled`는 단순한 만료 없음이 아닌 법적 보존 설정으로 구분합니다.
