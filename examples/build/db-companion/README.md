# db-companion OCI 빌드

소스: https://github.com/devmrko/db-companion

고정 커밋: `7a5cb34b68d8dae90f9c398667a0db976bb2f6d2`

이 디렉터리의 `build_spec.yaml`과 `build.sh`를 OCI Code Repository 루트에 둔다.
Managed Build의 기본 소스를 해당 저장소로 지정한다.
실행 이미지: `OL8_X86_64_STANDARD_10`.

- Temurin 21 다운로드 후 SHA-256 검증.
- 런타임 스크립트 테스트에 필요한 `lsof` 설치.
- 고정 커밋 체크아웃.
- Maven `quality` 프로파일의 `clean verify`.
- Node.js 테스트와 publication-check.
- JAR와 테스트 보고서를 Managed Build 출력으로 등록.

Registry 영구 저장은 별도의 Deliver Artifacts 단계가 필요하다.
GitHub 인증 정보, OCI 리소스 식별자, 개인 키를 파일에 넣지 않는다.
