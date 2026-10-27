# ARM Function 빌드 검증

OCI Function 내부에서 db-companion의 Maven 빌드를 실행한다. 앱 GUI에 연결하기 전의 실증 코드다.

- 실행: ARM, 3072MB, Detached 제한 3600초
- 소스: `devmrko/db-companion`, `func.py`의 고정 커밋
- 명령: `mvn -Pquality clean verify` — Java 테스트·PMD 포함
- 제외: JavaScript 테스트, 컨테이너 이미지 빌드, 배포
- 결과: Object Storage의 실행별 JAR·테스트 보고서·로그·`result.json`
- 인증: Function Resource Principal. 사용자 키나 Registry 토큰을 Function에 넣지 않는다.

## 준비

ARM 애플리케이션, 외부 HTTPS 통신이 가능한 서브넷, 비공개 OCIR 저장소와 결과 버킷이 필요하다.
`Dockerfile`은 Function의 실행 이미지를 만든다. db-companion 소스와 Maven 의존성은 Function 실행 중 내려받는다.

```sh
docker build --platform linux/arm64 -t <registry-image> examples/build/function-arm
docker push <registry-image>
```

이미지 기반 Function을 생성하고 다음 값을 지정한다.

| 항목 | 값 |
|---|---|
| 애플리케이션 아키텍처 | `GENERIC_ARM` |
| 메모리 | `3072` |
| 동기 제한 | `300` |
| Detached 제한 | `3600` |
| `BUILD_NAMESPACE` | Object Storage namespace |
| `BUILD_BUCKET` | 결과 버킷 |

Function에 해당 버킷의 `OBJECT_CREATE`, `OBJECT_OVERWRITE`만 허용한다. 예:

```text
Allow any-user to manage objects in compartment id <compartment-ocid> where all {request.principal.type='fnfunc', request.principal.id='<function-ocid>', target.bucket.name='<bucket>', any {request.permission='OBJECT_CREATE', request.permission='OBJECT_OVERWRITE'}}
```

## 실행

먼저 `{"action":"probe"}`를 동기로 호출해 실제 아키텍처·메모리·임시 공간을 확인한다.
`{"action":"build"}`를 Detached로 호출한다. HTTP 접수 성공은 빌드 성공이 아니다.
버킷의 `function-arm-<uuid>/result.json`이 `SUCCEEDED`인지, JAR 크기·SHA256이 일치하는지 확인한다.

소스·명령은 고정되어 있으며 요청으로 임의 셸 명령을 받지 않는다. 캐시 없이 실행하고 작업 디렉터리를 정리한다.
강제 종료·OOM이면 최종 결과 업로드가 불가능할 수 있다. `RUNNING` 기록이 남으면 OCI Logging과 Function 메트릭으로 확인한다.
메모리 측정값은 cgroup 기준이며 재사용된 Function 컨테이너의 이전 최대값이 포함될 수 있다.

## 공식 문서

- [ARM 아키텍처](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsspecifyingcomputearchitectures.htm)
- [메모리·실행 시간](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionscustomizing.htm)
- [Detached 실행](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsinvokingfunctions.htm)
