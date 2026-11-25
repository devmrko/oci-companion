# Function 이미지 빌더 검증 템플릿

Sustena의 소스·잠금 파일을 ARM Function에서 테스트하고, 설치 결과를 기반 이미지 위에 추가하는 실험용 템플릿입니다. Companion의 ARM 빌더 등록에서 Function 경로로 연결합니다. 실제 실행 결과와 제한은 설계·검증 문서에 기록합니다.

- 준비 이미지: Python 3.13 ARM64, fdk, uv, crane. 초기 Function 이미지를 준비할 때만 Dockerfile을 사용합니다. dependencies에는 동일 커밋의 pyproject.toml·uv.lock·vendor를 넣습니다.
- 배포 묶음에는 기존 `fn-python-template/source_bundle.py`를 함께 넣습니다. 별도 복제 구현을 만들지 않습니다.
- `IMAGE_BASE`: digest로 고정한 공개 Python 3.13 ARM64 기반 이미지.
- `IMAGE_REPOSITORY`: 이 Function이 push할 단일 OCIR 저장소. 호출자가 변경할 수 없습니다.
- `SOURCE_REPOSITORY`: 허용된 소스 전달 OCIR 저장소. 소스 이미지 digest와 내부 번들 SHA256을 모두 확인합니다.
- `probe`: 아키텍처·UID·Python·도구·임시 공간을 반환합니다.
- `build`: 고정 커밋, source.json을 포함한 최대 4MiB 소스 tar.gz의 base64·SHA256, 태그, 일회 OCIR 인증을 받습니다. 큰 소스는 sourceImage digest와 sourceSha256으로 전달합니다. 요청·인증값을 로그에 남기지 않습니다.
- 테스트: Function 이미지의 uv.lock 해시와 비교한 뒤, 사전 설치한 잠긴 개발 의존성으로 전달받은 앱 소스의 pytest를 실행하고 성공한 뒤 개발 의존성을 제거합니다. 설치된 site-packages만 레이어에 넣고 숫자 UID 10001, PYTHONPATH, Sustena 실행 명령을 지정합니다.
- 실패: 단계·소요 시간·마스킹한 오류를 반환합니다. 소스 작업과 레지스트리 인증 파일은 임시 디렉터리 정리 대상입니다. 강제 종료·호출 결과 불명확 시에는 같은 태그를 자동 재실행하지 말고 OCIR 결과를 확인해야 합니다.

새 앱·정책은 오너 승인 전에 생성하지 않습니다. 실제 사용 전에 기반 이미지 digest, crane 릴리스 체크섬, Function 최종 결과와 별도 OKE Ready·readyz를 확인해야 합니다. 이 템플릿은 임의 Dockerfile의 RUN을 실행하는 범용 빌더가 아닙니다.
