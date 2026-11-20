# 빌드 이미지 digest 출력

OCI 전달 결과의 `imageUri`·`deliveredArtifactHash`가 비어 있으면 빌드 명령에서 업로드 결과를 기록할 수 있다. 현재 태그를 나중에 조회해 과거 버전으로 간주하지 않는다.

## 출력 규약

OCI `env.exportedVariables`에 `BUILD_IMAGE_REFERENCE`를 선언한다. 업로드가 성공한 뒤 `registry/namespace/repository@sha256:64자리` 주소를 대입하고 export한다. 이름은 이 앱의 출력 규약이며 OCI 기본 변수는 아니다. 앱 없이도 OCI 실행 기록과 다음 단계에서 사용할 수 있다.

앱은 성공한 Managed Build 단계의 이 변수 하나만 읽는다. 일반 환경변수·Vault 변수·전체 출력 목록은 가져오지 않는다. 중복 이름, 태그만 있는 값, 실패한 단계는 이미지 증거로 사용하지 않는다. 단계별 한 이미지를 지원한다. 여러 이미지는 별도 단계에서 기록한다.

## 템플릿 사용

[업로드 스크립트](../../src/main/resources/static/templates/publish-build-image.sh)를 소스에 복사한다. 빌드 설정 화면에서도 다운로드할 수 있다. Docker 호환 CLI와 skopeo가 있는 빌드 환경이 필요하다. 스크립트가 런타임을 자동 설치하지 않는다.

```yaml
env:
  exportedVariables:
    - BUILD_IMAGE_REFERENCE
steps:
  - type: Command
    name: Publish image
    command: |
      set -euo pipefail
      set +x
      export LOCAL_IMAGE=my-app:build
      export IMAGE_REPOSITORY=icn.ocir.io/namespace/my-app
      export IMAGE_TAG="build-${OCI_BUILD_RUN_ID##*.}"
      export REGISTRY_AUTH_FILE=/path/to/temporary/auth.json
      . ./publish-build-image.sh
```

`REGISTRY_AUTH_FILE`은 실행 전에 Vault 값으로 만든 containers-auth.json 경로다. 예제 경로는 실제 임시 파일로 바꿔야 한다. Secret 값은 exportedVariables·명령 인자·Git에 넣지 않는다. 파일은 0600 권한으로 만들고 작업 종료 시 지운다. Registry 계정에는 대상 저장소 push 권한이 필요하다.

스크립트는 로컬 이미지의 아카이브를 업로드하고 `skopeo copy --digestfile` 결과를 기록한다. 업로드 실패 시 출력 변수를 내보내지 않는다. 이미지 아카이브와 임시 digest 파일은 정리한다. 인증 파일은 호출자가 관리한다.

## 배포

빌드 실행 화면의 ‘빌드 이미지 버전’에서 OCI 출력 주소를 확인한다. 배포 대상 선택에서 준비된 OKE 파이프라인을 고르면 명세의 이미지 주소·digest와 비교한다. 실행 전에 다시 검사한다. Registry 존재·아키텍처·pull 권한·앱 응답은 별도 점검이다.

이 출력은 빌드 스크립트가 선언한 결과다. 서명·SBOM·소스 코드의 신뢰성을 증명하지 않는다. 명세를 수정할 수 있는 주체가 결과를 기록할 수 있다는 점은 일반 CI 출력과 같다. OCI 전달 단계가 없는 경우 그 단계를 성공으로 표시하지 않는다.

근거: [OCI 빌드 명세](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm), [skopeo copy](https://github.com/podman-container-tools/skopeo/blob/main/docs/skopeo-copy.1.md).
