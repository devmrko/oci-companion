# 기존 서비스의 Namespace 이관

등록 서비스의 실행 설정을 다른 Namespace로 옮기고, 새 위치에서 실제 이미지와 Ready를 확인한 뒤 원본 워크로드를 정리합니다. 새 위치가 준비되지 않으면 원본을 유지하므로 단계별로 결과를 확인할 수 있습니다.

## 흐름과 경계

1. 새 서비스의 기존 설정 재사용에서 원본 서비스와 같은 클러스터의 대상 Namespace·새 등록 이름을 선택합니다. Namespace는 미리 존재해야 합니다.
2. 원본 Deployment의 실행 설정과 고정된 이미지 digest를 읽습니다. 대상의 같은 이름 Secret·ConfigMap·PVC·ServiceAccount·pull Secret 존재와 이름 충돌을 검사합니다. 값이나 볼륨 데이터를 복사하지 않습니다.
3. 미리보기 확인 후 새 OCI 환경·파이프라인·산출물과 대상 Deployment·ClusterIP를 생성합니다. 원본의 NodePort·Ingress·보안 규칙은 변경하지 않습니다.
4. 대상 Pod imageID·Ready를 확인한 후 새 서비스로 등록합니다. 여기까지는 원본이 계속 실행됩니다.
5. 이관 원본 정리 미리보기에서 대상 Pod를 다시 읽고 원본 변경 여부, 단일 ClusterIP, Ingress 참조 여부를 검사합니다. 외부 경로·데이터 확인 체크 후 원본 Deployment·같은 이름 ClusterIP만 삭제하고 원본 서비스 등록을 해제합니다.

삭제에는 API 측 UID·resourceVersion 조건을 사용합니다. 새 자원으로 교체되거나 동시 변경이 있으면 삭제를 거부합니다. 삭제 요청 전에 RETIRING 상태를 저장하여 응답이 불확실할 때 자동 재실행하지 않습니다. 두 자원의 삭제는 원자적이지 않으므로 일부만 삭제된 오류는 RETIREMENT_ATTENTION으로 남기며 조회 후 수동 복구가 필요합니다.

원본 Namespace·Secret·ConfigMap·PVC·배포 이력·조회 연결은 유지합니다. 이전 OCI 환경·파이프라인도 자동 삭제하지 않습니다. DNS·Ingress·NodePort 전환과 PVC 데이터 이동은 별도 작업이며 현재 자동 이관 범위에 포함하지 않습니다. 원본의 여러 Service나 외부 노출을 감지하면 정리를 거부합니다. 현재 단일 앱 컨테이너를 지원하고 대상 등록 이름은 원본과 달라야 합니다.

실제 결과는 [저장소 설정·Namespace 검증](../repository-deployment-import-validation.md)을 참고하세요.
