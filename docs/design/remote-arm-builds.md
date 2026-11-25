# 등록한 ARM VM에서 이미지 빌드

OCI 코드 저장소의 고정 커밋을 ARM VM으로 전달해 테스트·podman 빌드·OCIR push를 한 화면에서 진행한다. 실행 결과에 digest를 기록하고 같은 이미지 저장소의 배포 구성으로 이동한다.

빌더는 SSH 호스트·사용자·키 경로로 등록한다. 실행 전 소스 저장소와 이미지 저장소가 현재 구획에 있는지 확인하고 커밋·이미지 태그·테스트 명령을 미리보기로 보여 준다. VM은 실행 시 uname으로 ARM임을 확인한다. 소스는 OCI SDK archive API로 가져오고 경로·크기·파일 수를 검사한 뒤 새 임시 작업 디렉터리에만 전달한다.

빌드 명령은 등록한 VM에서 실행된다. 테스트 명령을 실행한 뒤 `podman build --platform linux/arm64`, `podman push --digestfile`을 실행한다. 인증정보를 입력하는 경우 일회성 stdin으로 전달하며 작업 결과·로컬 설정에는 저장하지 않는다. 입력하지 않으면 VM의 기존 podman 인증을 사용한다. 소스와 인증 임시 디렉터리는 실행 종료 시 지운다. 진행 상태와 digest만 반환하며 빌드 로그의 임의 비밀값을 화면에 출력하지 않는다.

현재 SDK가 제공하는 Managed Build 이미지 목록을 화면에서 새로 확인한다. ARM 이미지가 목록에 있으면 기존 Managed Build 이미지 선택에도 같은 SDK 목록이 반영된다. 이 확인은 OCI의 모든 지역·비공개 기능 지원을 입증하지 않는다. 현재 공식 안내는 OL8 기반 런타임이며, 실제 ARM 실행 성공과 구분해 표시한다. 교차 빌드(buildx·QEMU)는 느리고 별도 결과 검증이 필요한 대안으로 안내하며 자동 선택하지 않는다.

공식 근거: https://docs.oracle.com/en-us/iaas/Content/devops/using/add_buildstage.htm (2026-11-25 조회). 실제 원격 빌드·OCIR 업로드·배포 결과는 backlog 문서에 별도로 기록한다.
