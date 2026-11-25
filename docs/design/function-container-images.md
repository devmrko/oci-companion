# ARM Function에서 앱 이미지를 만드는 경로

기존 ARM Function의 소스 빌드 성공 경로를 컨테이너 이미지까지 확장합니다. 운영자는 등록 서비스의 빌더로 Function을 선택하고, 실패 시 기존 ARM VM 경로를 사용할 수 있게 하는 것이 목표입니다. ARM Function에서 테스트·레이어 생성·OCIR push와 별도 OKE 배포를 확인했습니다. [실제 결과와 제한](../function-container-image-validation.md)을 참고하세요.

첫 검증은 Sustena의 고정 커밋을 대상으로 합니다. Python 3.13 ARM Function에서 잠긴 의존성과 앱을 설치하고 테스트한 뒤, 설치 파일을 `/app/site-packages` tar 레이어로 만듭니다. 동일 Python 3.13 ARM64 기반 이미지에 crane으로 레이어를 추가하고 사용자·작업 경로·명령을 설정합니다. 이 과정에는 Docker 데몬·마운트·사용자 생성·권한 상승이 필요하지 않습니다. 숫자 UID 10001을 이미지 설정에 기록합니다.

기존 `source_bundle.py`의 크기 제한·경로 검사·커밋 확인을 재사용합니다. 작은 소스는 직접 전달할 수 있고, Sustena처럼 큰 소스는 설정한 비공개 OCIR 저장소에 소스 묶음을 이미지로 올려 digest·SHA256으로 확인합니다. 별도 Object Storage·새 IAM은 사용하지 않습니다. 기존 `ocir-pull`에서 읽은 인증은 실행에만 전달하고 소스·테스트 프로세스의 환경에 넣지 않습니다. 소스 다운로드 인증 파일은 테스트 전에 삭제합니다. push 인증 파일은 테스트 종료 후 별도 임시 경로에 생성하고 crane 실행 후 삭제합니다. 신뢰하는 소스만 실행하며 기존 Function처럼 소스 실행 자체가 보안 격리 경계는 아닙니다.

기반 이미지는 digest로 고정하고 ARM64 설정을 조회해 확인합니다. 출력 저장소는 서버 설정으로 제한하며 요청의 임의 레지스트리 주소로 인증을 전달하지 않습니다. 성공 판정에는 Function의 최종 결과, OCIR manifest digest, 별도 OKE 검증 서비스의 2/2 Ready와 readyz 응답이 필요합니다. 운영 Sustena 반영은 결과 확인 후 별도 승인 대상입니다.

참고: [crane 레이어 추가](https://github.com/google/go-containerregistry/blob/main/cmd/crane/doc/crane_append.md), [이미지 설정 변경](https://github.com/google/go-containerregistry/blob/main/cmd/crane/doc/crane_mutate.md). 기존 Maven 성공 기록은 [ARM Function 검증](../arm-function-build-validation.md)에 있습니다.

실제 첫 호출에서는 `/tmp`에 설치한 네이티브 Python 확장이 `failed to map segment from shared object`로 로드되지 않았습니다. 따라서 기존 Python Function의 사전 설치 의존성 패턴처럼 Function 실행 이미지에 동일 uv.lock의 의존성을 넣고, 전달된 앱 소스는 그 인터프리터로 테스트합니다. 출력 레이어용 의존성·앱 설치는 여전히 Function 안에서 수행합니다. 원본 uv.lock과 실행 이미지의 잠금 파일 해시가 다르면 먼저 실행 이미지를 갱신해야 합니다. 임의 소스의 임의 RUN을 지원하는 빌더는 아닙니다.

Companion 서버는 `companion.crane`으로 지정한 crane을 사용해 소스 이미지만 업로드합니다. Function 호출에는 기존 OCI 인증과 재시도 없는 SDK 클라이언트를 사용합니다. Function 설정·구획·ARM shape·계약·출력 저장소를 미리보기와 실행에서 비교합니다. 결과 digest는 기존 ARM 실행기의 OCIR 조회로 다시 검증합니다. VM 실행 경로와 기존 저장 설정은 유지합니다. 소스 이미지 태그는 전달 이력이며 주기적 정리가 필요합니다. 검증용 저장소는 정리하고 운영 Function의 런타임·소스 전달 저장소는 유지합니다.
