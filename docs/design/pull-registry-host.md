# 이미지 주소와 pull Secret 레지스트리

배포 담당자가 기존 pull Secret을 그대로 사용할 수 있도록 이미지 주소를 Secret의 레지스트리 호스트와 맞춘다. 배포 화면에서 자동 선택·리전 이름·리전 키를 선택하며 미리보기에서 불일치를 확인한다.

기존 `OkeRuntimeResources`에 선택한 Secret 하나의 레지스트리 조회를 추가한다. 목록 조회는 계속 메타데이터만 읽는다. 별도 조회는 Kubernetes Secret get 권한이 필요하며 dockerconfigjson을 메모리에서만 파싱한다. 반환 결과에는 auths의 호스트 이름만 포함하고 인증값은 저장·로그·오류·HTTP 응답에 넣지 않는다.

자동 선택은 현재 리전의 두 OCIR 주소 중 Secret에 있는 호스트만 사용한다. 임의 Registry나 다른 리전으로 이미지를 바꾸지 않는다. 조회 실패는 UNKNOWN으로 표시하여 확인 없이 구성을 저장하지 않는다. 명시적 주소가 Secret과 다르면 WARN을 표시한다.

이미지 digest는 그대로 유지한다. 생성 직전 기존 미리보기 재검증을 통해 Secret 호스트가 바뀌어 이미지 주소가 달라지면 다시 미리보기하도록 한다. 실제 image pull 성공 여부는 OCI 검증에서 별도로 확인한다.
