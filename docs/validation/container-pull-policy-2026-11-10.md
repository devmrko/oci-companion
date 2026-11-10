# 사설 OCIR의 Container Instances policy 검증

2026-11-10, DEFAULT / 서울 / 검증 구획. 사용자의 policy 적용 요청에 따라 검증용 인스턴스와 `companion/container-demo` 저장소에 읽기 권한을 적용했다. 기존 동적 그룹은 변경하지 않았다.

## 정책 적용 순서

첫 시도는 인스턴스를 화면에서 생성하고 OCID 한 대로 제한한 정책을 적용했다. 정책 저장은 성공했지만 인스턴스는 이미지 pull 인증 오류로 실패했다. 첫 다운로드와 정책 전파의 선후관계는 확정하지 못했다. 해당 인스턴스 주체의 Audit 조회 결과가 없었으며, 이를 권한 요청이 없었다는 증거로 보지 않는다.

재시도에서는 정책을 먼저 준비했다. 서울 리전·검증 실행 구획의 Container Instances 주체만 해당 저장소를 읽도록 제한했다. 신규 동적 그룹은 만들지 않았다.

```text
Allow any-user to read repos in compartment id <저장소 구획 OCID> where all {
  request.principal.type='computecontainerinstance',
  request.principal.compartment.id='<실행 구획 OCID>',
  request.region='ICN',
  target.repo.name='companion/container-demo'
}
```

생성 전 정책은 해당 구획의 현재·향후 Container Instances에 적용된다. 일반 사용 시 전용 실행 구획을 사용하고, `any-user`의 제한 조건을 제거하지 않는다. 실행 구획과 저장소 구획은 서로 다를 수 있다.

재생성한 인스턴스가 성공한 후 기존 정책 문장을 `request.principal.id='<성공한 인스턴스 OCID>'`로 교체했다. 구획 단위 허용 문장은 남기지 않았으며 OCI 정책 재조회로 교체를 확인했다. 서울·저장소 이름·주체 유형 조건은 유지했다.

## 실제 결과

- 앱 화면에서 기존 사설 이미지 digest를 선택해 1 OCPU·2GB·공용 IP 없는 인스턴스를 생성했다. 기존 검증 서브넷을 사용했다.
- OCI에서 인스턴스·컨테이너 `ACTIVE`, HTTP 검사 `HEALTHY`를 확인했다.
- 화면에서 같은 이미지 digest와 로그의 `GET / HTTP/1.1` 응답 `200`을 확인했다.
- 정책을 한 대로 좁힌 후 화면에서 중지·시작을 수행해 `ACTIVE`·HTTP 정상·로그를 다시 확인했다. 검증 종료 시 성공 인스턴스는 `INACTIVE`로 중지해 보존하고 해당 인스턴스의 저장소 읽기 정책은 유지한다. 첫 실패 인스턴스는 삭제했다.
- 도움말에 생성 전 policy와 생성 후 인스턴스별 policy, 콘솔 적용 위치를 추가했다. 선택한 인스턴스의 실제 OCID를 예제에 표시한다.
- Java 테스트 259개와 실제 OCI 화면의 도움말·390·768·1440px 검증을 통과했다.

이번 결과는 기존 amd64 이미지 한 개와 검증 네트워크에 한정된다. 다른 리전·ARM·외부 Registry·새 네트워크는 검증하지 않았다. 정책 저장만으로 이미지 pull 성공을 판단하지 않는다.

공식 근거: [일반 정책 변수](https://docs.oracle.com/en-us/iaas/Content/Identity/policyreference/policyreference_topic-General_Variables_for_All_Requests.htm), [저장소 읽기 권한](https://docs.oracle.com/en-us/iaas/Content/Registry/Concepts/registrypolicyrepoaccess.htm). OCI가 제공하는 조건을 조합한 방식이며, 모든 테넌시 정책 조합에 대한 보장은 아니다.

로컬 근거: `.tools/ci-policy-iam.json`, `ci-policy-retry-created.json`, 상태 JSON·브라우저 로그·캡처. 개인 OCID와 전체 로그는 공개 문서에 넣지 않는다.
