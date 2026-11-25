package io.github.devmrko.companion.port;
import java.util.List;
import java.util.Map;
/** Immutable expectations captured before execution, and read-only Kubernetes evidence. */
public interface DeploymentImageVerification {
 record Expected(String namespace,String deployment,int replicas,Map<String,String> images) {}
 record Image(String workload,String pod,String container,String expected,String actual,boolean ready) {}
 record Result(String state,String detail,List<Image> images) {
  public static Result unknown(){return new Result("UNVERIFIED","실제 Pod 이미지·Ready 확인 불가 · 클러스터 연결과 조회 권한을 확인하세요.",List.of());}
 }
}
