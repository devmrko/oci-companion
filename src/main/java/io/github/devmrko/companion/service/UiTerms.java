package io.github.devmrko.companion.service;
import java.util.*;
import org.springframework.stereotype.Component;
/** UI vocabulary only. OCI resource identity and API fields remain unchanged. */
@Component("uiTerms")
public class UiTerms {
 public record Term(String key,String label,String nativeName,String description,String console,String cli) {}
 private final List<Term> terms=List.of(
  new Term("compartment","리소스 구획","OCI Compartment","리소스·권한 관리 범위","Identity & Security → Compartments","oci iam compartment list --compartment-id <상위 구획 OCID> --all"),
  new Term("source","소스 저장소","Git Repository / OCI Code Repository","빌드할 코드와 브랜치","Developer Services → DevOps → 프로젝트 → Code Repositories","oci devops repository list --project-id <프로젝트 OCID> --all"),
  new Term("project","프로젝트","OCI DevOps Project","소스·파이프라인·로그의 관리 단위","Developer Services → DevOps → Projects","oci devops project list --compartment-id <구획 OCID> --all"),
  new Term("pipeline","빌드 파이프라인","OCI Build Pipeline","빌드·저장·배포 호출 순서와 설정","DevOps → 프로젝트 → Build Pipelines","oci devops build-pipeline list --compartment-id <구획 OCID> --all"),
  new Term("stage","빌드 단계","OCI Build Pipeline Stage","명령 실행·산출물 저장 작업","DevOps → 프로젝트 → Build Pipelines → 파이프라인","oci devops build-pipeline-stage list --build-pipeline-id <파이프라인 OCID> --all"),
  new Term("spec","빌드 명세","Build Specification · build_spec.yaml","빌드 명령·환경변수·출력 파일 설정","DevOps → Build Pipeline → Managed Build → Build spec file","oci devops build-pipeline-stage get --build-pipeline-stage-id <단계 OCID>"),
  new Term("registry","산출물 저장소","Artifact Registry / Container Registry","빌드한 파일·컨테이너 이미지 보관","Developer Services → Artifact Registry / Container Registry","oci artifacts repository list --compartment-id <구획 OCID> --all"),
  new Term("target","배포 대상","VM / OKE / Container Instances / Functions","배포한 프로그램을 실행할 환경","Compute / Kubernetes Clusters / Container Instances / Functions","oci compute instance list --compartment-id <구획 OCID> --all"),
  new Term("topic","알림 채널","OCI Notifications Topic","DevOps 이벤트 알림의 수신 주제","Developer Services → Notifications → Topics","oci ons topic list --compartment-id <구획 OCID> --all")
 );
 public Term get(String key){return terms.stream().filter(t->t.key().equals(key)).findFirst().orElseThrow();}
 public List<Term> all(){return terms;}
}
