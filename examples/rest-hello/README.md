# REST hello

Python, JavaScript, TypeScript, Go, Ruby, Java의 독립 실행 예제.

| 요청 | 응답 |
| --- | --- |
| `GET /hello` | 200 · `{"message":"hello","language":"python"}` (언어별 값) |
| `GET /missing` | 404 · JSON 오류 |
| `POST /hello` | 405 · JSON 오류 |

## 로컬 실행

저장소 루트에서 실행한다. Docker가 필요하다.

```sh
docker build -t hello-python examples/rest-hello/python
docker run --rm -p 8080:8080 hello-python
```

다른 터미널에서 `curl http://localhost:8080/hello`로 확인한다.
폴더명을 `javascript`, `typescript`, `go`, `ruby`, `java`로 바꿔 다른 예제를 빌드한다.

## 빌드·검증

x86 Linux와 Docker 환경에서 실행한다. 앱 컨테이너와 검사 컨테이너는 종료 시 삭제한다.

```sh
bash examples/rest-hello/build.sh python
```

결과는 `examples/rest-hello/reports/python.xml`에 저장한다. 각 언어에서 HTTP 3건을 검사한다.

## OCI DevOps

검증용 OCI Git 브랜치 `examples/rest-hello-20261102`에는 이 폴더를 루트의 `rest-hello/`로 복사했다. 제공한 명세는 이 배치를 사용한다.

1. Managed Build의 명세 경로: `rest-hello/<언어>/build_spec.yaml`.
2. x86 빌드 환경을 선택한다. 예제에서 검증한 이미지는 `OL8_X86_64_STANDARD_10`.
3. 전달 단계에 `hello-image`(OCIR)와 `test-reports`(Artifact Registry)를 연결한다.
4. 보고서 버전에 `${REPORT_VERSION}`을 사용한다. 재실행마다 새 값을 입력한다. 예: `manual-20261102-01`.

앱의 **빌드 → 빌드 작업 → Hello REST · 언어 · x86 → 실행 이력·로그**에서 로그와 테스트 집계를 확인한다. 이미지는 **환경 관리 → 저장소**의 `companion/hello-<언어>`에서 확인한다.

이미지는 HTTP 서버용이며 Function 호출 규약을 구현하지 않는다. ARM 빌드와 상시 배포는 이 예제 검증에 포함하지 않았다. 기반 이미지 태그는 바뀔 수 있으므로 검증 결과를 그대로 재사용할 때는 OCIR digest를 지정한다.
