package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.database.requests.GetAutonomousDatabaseRequest;
import com.oracle.bmc.containerengine.ContainerEngineClient;
import com.oracle.bmc.containerengine.requests.GetClusterRequest;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class OciAgentDependencies {
 public record Snapshot(String compartment,String state) {}
 public record Status(AgentConnectionRegistry.Dependency reference,String state,String checkedAt,String guidance,boolean verified) {}
 private final OciBuildHistory cloud;
 public OciAgentDependencies(@org.springframework.beans.factory.annotation.Value("${companion.oci-config}") String path){this.cloud=new OciBuildHistory(path);}
 Snapshot fetch(Connection c,String region,AgentConnectionRegistry.Dependency ref)throws Exception {
  var auth=cloud.authentication(c);
  var config=ClientConfiguration.builder().connectionTimeoutMillis(2000).readTimeoutMillis(3000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();
  return switch(ref.type()){
   case "VM" -> {try(var client=ComputeClient.builder().configuration(config).build(auth)){client.setRegion(region);var x=client.getInstance(GetInstanceRequest.builder().instanceId(ref.ocid()).build()).getInstance();yield new Snapshot(x.getCompartmentId(),x.getLifecycleState()==null?"UNKNOWN":x.getLifecycleState().getValue());}}
   case "ADB" -> {try(var client=DatabaseClient.builder().configuration(config).build(auth)){client.setRegion(region);var x=client.getAutonomousDatabase(GetAutonomousDatabaseRequest.builder().autonomousDatabaseId(ref.ocid()).build()).getAutonomousDatabase();yield new Snapshot(x.getCompartmentId(),x.getLifecycleState()==null?"UNKNOWN":x.getLifecycleState().getValue());}}
   case "OKE" -> {try(var client=ContainerEngineClient.builder().configuration(config).build(auth)){client.setRegion(region);var x=client.getCluster(GetClusterRequest.builder().clusterId(ref.ocid()).build()).getCluster();yield new Snapshot(x.getCompartmentId(),x.getLifecycleState()==null?"UNKNOWN":x.getLifecycleState().getValue());}}
   default -> throw new IllegalArgumentException();
  };
 }
 public Status check(Connection c,String region,String compartment,AgentConnectionRegistry.Dependency ref){
  try {
   if(c.demo())throw new IllegalArgumentException();
   var x=fetch(c,region,ref);
   if(!Objects.equals(compartment,x.compartment()))return new Status(ref,"범위 불일치",Instant.now().toString(),"현재 구획의 리소스를 등록하세요.",false);
   String guidance=switch(x.state()){
    case "STOPPED" -> "중지 상태입니다. 운영 정책과 시작 권한을 확인하세요. 앱 오류의 확정 원인은 아닙니다.";
    case "STARTING", "PROVISIONING", "UPDATING" -> "전환 중입니다. 완료 후 다시 확인하세요.";
    default -> switch(ref.type()) {case "OKE" -> "클러스터 제어부 상태입니다. 노드 Ready와 Pod 상태는 별도로 확인하세요.";case "ADB" -> "DB 리소스 상태입니다. 앱의 DB 인증·네트워크·쿼리 성공은 별도로 확인하세요.";default -> "VM 상태입니다. 앱 프로세스·터널·네트워크는 별도로 확인하세요.";};
   };
   return new Status(ref,x.state(),Instant.now().toString(),guidance,true);
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return new Status(ref,"조회 불가",Instant.now().toString(),"조회 권한·OCID·리전·OCI 연결을 확인하세요. 중지 여부는 확인되지 않았습니다.",false);}
 }
}
