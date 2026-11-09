# 배포 전 OCIR digest 조회

2026-11-10. 저장된 OKE 배포 명세의 이미지 주소를 OCI Artifacts SDK로 조회합니다. 교체 가능한 `DeploymentImages` 인터페이스를 추가했습니다. 이미지 주소의 호스트로 임의 HTTP 요청을 보내지 않습니다.

## 판정

- 현재 리전 OCIR 주소, 선택 구획의 정확한 저장소 이름·namespace, digest와 AVAILABLE 상태를 확인하면 ‘확인’입니다.
- 정확한 저장소를 조회한 뒤 모든 이미지 결과 페이지에서 digest가 없거나, 일치 이미지가 DELETED/DELETING이면 ‘실행 불가’입니다. 미리보기의 실행 티켓과 버튼을 만들지 않습니다.
- 권한·연결 오류, 다른 리전/Registry/구획, namespace 불일치, 조회 한도 초과는 ‘미확인’입니다. 없음으로 단정하거나 새 차단을 적용하지 않습니다.
- 실행 직전에도 설정과 이미지 digest를 다시 조회합니다. 확인 이후 이미지가 사라지면 실행 요청을 차단합니다. 조회와 OCI 실행은 원자적 작업이 아니므로 그 사이의 삭제 가능성까지 없애지는 못합니다.
- 최대 20개 고유 이미지, 조회별 최대 10페이지를 검사합니다. 한도를 넘으면 미확인입니다. 인증값·이미지 본문을 로컬에 저장하지 않습니다.

## 아키텍처 제한

[OCI ContainerImage 명세](https://docs.oracle.com/en-us/iaas/tools/python/latest/api/artifacts/models/oci.artifacts.models.ContainerImage.html)와 설치 SDK를 확인했습니다. digest·상태·레이어 메타데이터는 있지만 CPU architecture 필드는 없습니다. 이 변경은 **digest 존재·상태 검사**이며 x86/ARM 자동 대조는 완료하지 않았습니다. 이미지명·태그로 아키텍처를 추정하지 않습니다. 후속 구현에는 Registry manifest/config를 읽는 인증 경로와 실제 배포 대상 아키텍처 검증이 필요합니다. 이미지 조회 성공이 실행 주체의 pull 권한 성공을 뜻하지도 않습니다.

## 검증

- Java 224개 통과: 외부 호스트 미조회, 정확한 namespace·구획·저장소 확인, 빈 digest 결과와 조회 오류 구분, 페이지 순회, 실행 티켓 차단, 실행 직전 이미지 재검증.
- 실제 DEFAULT / ap-seoul-1 / Joungmin.Ko의 전용 OKE 파이프라인에서 기존 OCIR digest AVAILABLE 표시와 실행 버튼 확인.
- 동일 전용 명세의 digest만 일시적으로 존재하지 않는 값으로 변경. 실제 OCI 조회 결과 ‘실행 불가’, 실행 버튼 없음 확인.
- 원래 명세를 ETag로 복원하고 OCI에서 원본 일치 확인. 배포 실행·VM 시작·IAM 변경 없음.
- 실제 브라우저 390/768/1440px 페이지 가로 넘침 없음. `scripts/browser-deployment-images.cjs` 사용. 로컬 증거 `.tools/deployment-image-available.png`, `.tools/deployment-image-missing.png`.

다른 리전·구획 및 외부 Registry의 존재 여부와 x86/ARM 호환성, 실제 pull·앱 응답은 이 검증 범위 밖입니다.

후속 구현: [동일 프로파일의 Registry 토큰으로 manifest 조회·명세 아키텍처 대조](image-architecture-2026-11-10.md). 위 API 필드 제한은 그대로이며 별도 네이티브 API 경로로 보완했습니다.
