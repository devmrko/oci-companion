# 환경 준비 화면 검증

2026-11-09 KST. DEFAULT / ap-seoul-1 / Joungmin.Ko. 테스트 서버 8092에서 실제 OCI 프로파일로 로그인하고 화면의 폼·버튼을 사용했다. 단위 테스트의 OCI 대역은 실패·범위·중복 요청 조건 검증용이며 실제 화면에는 사용하지 않았다.

## VM

대상: 기존 `oci-agent-launchpad-demo-vm-ol9`, `VM.Standard.E5.Flex`, 원래 STOPPED, 1 OCPU / 8GB.

- 화면에서 메모리 8→9→8GB 변경·복원 성공.
- 후속 화면에서 1 OCPU / 8GB → 2 OCPU / 9GB → 1 OCPU / 8GB 변경·복원 성공.
- 자동 VM 시작 없음. 독립 SDK 조회에서 원래 사양과 STOPPED 상태를 확인했다.
- 연결된 50GB 부트 볼륨 확장 미리보기와 축소 불가 안내를 확인했다. 기존 볼륨 실제 확장·파일시스템 변경은 하지 않았다.
- POST 미리보기에서 상단 리전·구획이 빠지는 문제를 발견해 수정했다.
- 증거: `.tools/vm-sizing-mutation-ui.json`, `vm-sizing-applied.png`, `vm-sizing-volume-preview.png`.

## OKE

- 실제 클러스터 2개 조회: `document_search`(공용), `launchpad-oke`(사설).
- 사설 클러스터에서 `launchpad-oke-nodes`, 2 OCPU / 16GB / 부트 100GB / 노드 1개 설정을 화면에 표시했다. 이 값은 노드 풀 설정이며 Pod·노드 정상 상태를 의미하지 않는다.
- 사설 서브넷 선택과 공용 클러스터의 접속 서브넷 입력 생략을 확인했다.
- 공용 클러스터를 기존 db-companion 프로젝트에 `companion-oke-preparation-20261109` 이름으로 실제 연결했다.
- 환경 OCID: `ocid1.devopsdeployenvironment.oc1.ap-seoul-1.amaaaaaavsea7yiavbxqnspe64sk24v2cjqnu2z76anwfmzf4x7yf76ydwxq`.
- 사설 환경 생성과 신규 클러스터 생성은 실행하지 않았다. 기존 노드·앱·네트워크 규칙도 변경하지 않았다.
- 증거: `.tools/oke-preparation-created.json`, `oke-preparation-private.png`, `oke-preparation-final.png`.

## Container Instances

- 서울 리전 조회 결과: CI.Standard.Ampere.Generic, CI.Standard.E4.Flex, CI.Standard.x86.Generic. 하드코딩한 Shape 목록을 사용하지 않는다.
- 기존 `companion/hello-python:x86-20261102-v1` 이미지 선택. digest `sha256:f77423f9a0d567f5854225a7949e894ed2f28b729c2dfef7a658ceaa922cc48b`로 고정.
- SDK 상태 Enum의 toString(Available)과 API 값(AVAILABLE) 차이로 이미지 선택이 비는 문제를 수정했다. Registry 포트에서 API 값을 반환하고 회귀 테스트를 추가했다.
- 생성 JSON 다운로드 확인: E4 Flex, 1 OCPU / 2GB, 기존 검증 서브넷, 공용 IP 없음. 네이티브 HTTP check /hello:8080, 15초 간격, 실패 시 자동 종료 없음.
- 실제 생성 요청 접수: `companion-ci-validation-20261109`, `ocid1.computecontainerinstance.oc1.ap-seoul-1.anuwgljrvsea7yiahvm2fo4efmcugxd3wnoo2oi4znipv5zbadddwriucqna`.
- 첫 생성은 OCI Work Request에서 `400: A container's image could not be pulled due to inadequate network configuration.`으로 실패·롤백됐다. 기존 검증 VM의 서브넷은 Internet Gateway만 있고 Container에는 공용 IP가 없어 OCIR 접근 경로가 없었다. 사설 생성 미리보기에 NAT/전체 OCI Service Gateway 경로 검사를 추가했다. 기존 `launchpad-oke-nodes` 사설 서브넷으로 재검증한다. 기존 네트워크 규칙은 변경하지 않았다.

## 화면·보호 검사

390/768/1440px에서 페이지 가로 넘침과 JavaScript 오류가 없었다. 네이티브 JSON에는 앱 전용 실행 의존성이나 Registry 인증정보가 없다. HTTP 경로·환경변수·CPU/메모리 범위, 다른 구획·부모 리소스, stale snapshot, ETag, CSRF, 티켓 중복 적용을 검사한다.
