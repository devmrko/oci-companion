# 사설 컨테이너 빌드·배포 검증

2026-11-10, DEFAULT / ap-seoul-1 / Joungmin.Ko. 실제 OCI와 8091 앱 화면을 사용했습니다.

## 결과

| 단계 | 결과 |
| --- | --- |
| 컨테이너 빌드 | 화면에서 기존 전용 파이프라인 실행, 테스트·이미지 빌드·사설 OCIR 전달 성공 |
| OKE 배포 | 화면에서 새 구성 저장·실행, 재시도 성공, 성공 상태·로그 확인 |
| 앱 응답 | 준비된 Pod에 localhost port-forward 후 브라우저 HTTP 200, `status=ok`, `architecture=x86_64` |
| 이미지 일치 | 실행 Pod의 imageID가 이번 빌드의 OCIR digest와 일치 |
| Container Instances | 생성 요청은 성공. 사설 이미지 pull 인증 실패. 필요한 동적 그룹 생성은 테넌시 한도로 거절됨 |
| 오류 도움말 | 실제 실패 화면에서 사설 OCIR 권한·그룹 한도 안내 확인. 390/768/1440px 가로 넘침 없음 |

이미지 digest: `sha256:17d7c567cd48ae299462e293886f267f0b9dd165f0ac4c9b36882038353bea5e`.

## OKE 실행 조건

기존 클러스터·프로젝트·미러 소스·빌드 파이프라인을 재사용했습니다. 새 namespace `companion-container-flow`에 기존 Vault 인증을 읽어 pull Secret을 준비했습니다. 인증값은 출력하지 않았습니다. 테스트 앱은 Python HTTP 서버이며 amd64 이미지입니다.

첫 배포는 권한 오류로 실패했습니다. Audit에서 클러스터 조회와 알림 주제 조회 거절을 확인했습니다. 정확한 파이프라인·클러스터에 제한한 정책을 확인하고, 해당 파이프라인의 프로젝트 알림 주제 접근을 추가한 뒤 재시도했습니다. 재시도는 성공했습니다. 최초 실패에 IAM 전파 시간이 미친 영향은 분리 검증하지 않았습니다.

빌드·배포 실행과 상태·로그는 앱 화면으로 확인했습니다. SDK와 kubectl은 인증 준비, 읽기 진단, digest 검증, 포트 전달 및 정리에 사용했습니다. 공개 인그레스, 새 테넌시 최초 구성, ARM 이미지, 이번 이미지의 버전 간 복구는 이 검증에 포함하지 않습니다.

## Container Instances 제한과 보완

정확한 새 인스턴스 한 대를 대상으로 동적 그룹을 만들려 했으나 OCI가 `DynamicResourceGroups` 객체 한도 오류로 거절했습니다. 조회된 그룹 수는 100개였습니다. 새 그룹·정책은 생성되지 않았고 기존 그룹을 삭제하거나 권한을 확대하지 않았습니다. 인스턴스는 이미지 pull 오류로 FAILED가 됐습니다.

사설 OCIR은 Container Instance 리소스 주체의 저장소 읽기 권한이 필요합니다. 사용자의 이미지 목록 조회 성공과 다릅니다. 화면에 인스턴스 한 대·저장소 이름으로 제한하는 예시, 동적 그룹 한도 시 관리자 확인 경로, 실패 후 재생성 절차를 추가했습니다. Vault 인증으로 사설 OCIR 권한을 대신하도록 안내하지 않습니다.

- [OCI Container Instances 이미지 인증](https://docs.oracle.com/en-us/iaas/Content/container-instances/vault-secrets-image-authorization.htm)
- [Container Instances IAM](https://docs.oracle.com/en-us/iaas/Content/container-instances/permissions/policy-reference.htm)

이 초기 시점에는 사설 OCIR → Container Instances의 정상 실행이 **미완료**였다. 이후 사용자 요청으로 조건부 policy를 적용해 실행에 성공했다. [후속 policy 검증](container-pull-policy-2026-11-10.md)을 참고한다.

## alpha.9 이후 인증 재점검

2026-11-10 실제 IAM 목록과 개별 동적 그룹 100개의 규칙을 읽었다. Container Instances 리소스 유형이 명시된 그룹 3개는 다른 구획용이었다. 이를 현재 검증 구획으로 확장하거나 다른 사용자의 그룹을 재사용하지 않았다. 모든 규칙·정책 조합의 유효 권한을 계산한 것은 아니며, 그룹 개수만으로 신규 생성 한도를 다시 확정하지도 않는다. 앞선 한도 실패 이후 이번에는 신규 그룹·인스턴스 생성을 재시도하지 않았다.

공식 이미지 인증 문서는 사설 OCIR에 리소스 주체 IAM을, 외부 Registry에 Vault 인증을 안내한다. Vault를 동적 그룹 문제의 우회 방법으로 사용하지 않았다. 실제 배포 검증을 재개하려면 관리자가 사용할 동적 그룹과 해당 저장소 읽기 정책을 준비해야 한다. 기존 그룹 재사용은 연결 정책과 다른 리소스에 미치는 영향까지 확인해야 한다.

화면에서는 OCIR 이미지 선택 바로 아래에 IAM 방식을 표시하고, 외부 Registry의 Vault 선택란 이름과 빈 선택값을 구분했다. Vault 목록 조회 실패 안내에도 OCIR IAM은 Secret 선택이 필요하지 않다고 명시했다. 실제 OCI 가용 영역·Shape 조회 화면에서 안내를 펼쳐 확인했으며 390·768·1440px 가로 넘침과 브라우저 오류가 없었다. Java 테스트 259개 통과. 이번 변경은 안내 보완이며 사설 이미지 실행 검증 완료를 뜻하지 않는다.

## 정리·근거

실패한 Container Instance는 화면에서 삭제 요청 후 DELETED를 확인했습니다. OKE 검증 namespace와 pull Secret, 임시 파이프라인 정책을 삭제했습니다. 빌드 이미지와 OCI 배포 이력·구성은 확인을 위해 보존했습니다.

Java 테스트 219개 통과. 실제 실패 화면 검증 스크립트: `scripts/browser-private-container-help.cjs` (전용 실패 인스턴스가 있을 때 실행, 삭제는 명시적 환경변수 필요).

로컬 증거: `.tools/private-container-flow.json`, `private-container-build-ui.png`, `private-oke-ui.txt/png`, `private-oke-http.json/png`, `private-oke-pods.json`, `private-ci-help-{390,768,1440}.png`, `private-ci-cleanup.json`, `private-oke-policy-cleanup.json`. 인증·개인 리소스 데이터가 포함될 수 있어 저장소에 올리지 않습니다.
