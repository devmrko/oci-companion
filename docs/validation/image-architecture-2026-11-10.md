# OCI 프로파일로 이미지 아키텍처 대조

2026-11-10. OCIR 사설 이미지의 플랫폼을 프로파일 서명으로 조회하고, 저장된 OKE 명세의 `kubernetes.io/arch` nodeSelector와 비교합니다. 별도 Docker 설치·비밀번호·Vault Secret 설정은 추가하지 않았습니다.

## 네이티브 인증·조회

OCI Container Registry `GetAccessToken` SDK는 프로파일 서명을 지원합니다. 선택한 저장소에 `pull`만 요청하는 토큰을 받아 Registry v2의 manifest/config를 읽습니다. 토큰은 호출 중 메모리에서만 사용하며 출력·로컬 저장하지 않습니다. 기존 SDK 버전 3.94.1의 containerregistry 모듈을 추가했습니다.

[GetAccessToken 공식 명세](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/container-registry/access-token/get.html), [OCI Image Index](https://github.com/opencontainers/image-spec/blob/main/image-index.md), [Image Config](https://github.com/opencontainers/image-spec/blob/main/config.md).

이전 검증에서 ContainerImage 제어 API에 아키텍처 필드가 없음을 확인했지만, 같은 프로파일로 Registry 데이터 API를 읽는 경로는 확인하지 못했습니다. 이번에 실제 서명 토큰·manifest·config 조회 HTTP 200과 linux/amd64 반환을 확인했습니다.

## 판정과 경계

- 단일 이미지: manifest와 config를 읽고 각각 SHA-256을 확인합니다.
- 다중 아키텍처: digest 검증한 OCI index/Docker manifest list의 플랫폼을 사용합니다. 알려진 attestation 항목은 제외합니다. 알 수 없는 항목·중첩 index는 전체 플랫폼을 확인했다고 처리하지 않습니다.
- 모든 명세의 일반·init 컨테이너에 대해 nodeSelector를 수집합니다. 하나의 이미지를 여러 아키텍처로 사용하는 경우 모두 비교합니다.
- 완전히 해석한 이미지 플랫폼과 명시적인 amd64/arm64 요구가 불일치하면 미리보기 티켓·실행 버튼을 막고, 제출 시에도 다시 확인합니다.
- nodeSelector 없음, 조회 권한·연결 오류, 외부 Registry, 불완전한 목록 등은 미확인입니다. 이미지명이나 태그로 추정하지 않습니다.
- 성공 표시는 **이미지와 명세의 CPU 아키텍처 일치**입니다. 실제 OKE 노드의 존재·라벨·OS·CPU 세부 기능·affinity·taint·용량·스케줄링 성공을 확인한 것은 아닙니다. 실행 주체의 pull 인증과도 별개입니다.

주소는 선택 리전의 SDK endpoint와 검증한 저장소 경로로 구성합니다. 임의 호스트·descriptor URL을 호출하지 않으며 HTTP 리다이렉트도 따라가지 않습니다. 메타데이터는 요청당 1MiB, HTTP 응답은 15초, 목록은 100개 descriptor, 한 번에 20개 고유 이미지로 제한합니다. 이미지 레이어는 받지 않습니다.

## 검증

- Java 230개 통과. 단일·다중 이미지, manifest/config digest 불일치, 알 수 없는 index 항목, 임의 config 경로, 응답 크기 제한, 명세 요구 비교, 미리보기 및 제출 시 차단 포함.
- 실제 DEFAULT / ap-seoul-1 / Joungmin.Ko의 사설 이미지: linux/amd64와 명세 amd64 일치 표시, 실행 버튼 확인.
- 전용 검증 파이프라인의 nodeSelector만 arm64로 임시 변경: 실제 이미지 조회 후 ‘실행 불가’, 실행 버튼 없음 확인.
- 명세를 ETag로 원복하고 OCI에서 원본 일치 확인. 배포 실행·VM 시작·IAM 변경 없음.
- 390/768/1440px 페이지 가로 넘침 없음. 브라우저 JavaScript 오류 없음.

스크립트: `scripts/browser-deployment-architecture.cjs`. 로컬 증거: `.tools/deployment-architecture-match.png`, `.tools/deployment-architecture-mismatch.png`. 실제 다중 아키텍처 이미지의 클라우드 조회는 이번에 수행하지 않았으며 파서 테스트 범위입니다.
