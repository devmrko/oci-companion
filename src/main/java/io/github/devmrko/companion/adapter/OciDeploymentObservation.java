package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentObservation;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class OciDeploymentObservation implements DeploymentObservation {
 private final OciBuildHistory clients;
 public OciDeploymentObservation(@Value("${companion.oci-config}") String config){clients=new OciBuildHistory(config);}
 com.oracle.bmc.devops.DevopsClient client(Connection c,String r)throws Exception{return clients.client(c,r);}
 public Context read(Connection c,String r,String comp,String pipeline,String run){
  if(c.demo())throw new IllegalArgumentException("실제 OCI 연결을 선택하세요.");
  try(var client=client(c,r)){
   var old=client.getDeployment(GetDeploymentRequest.builder().deploymentId(run).build()).getDeployment();
   if(!comp.equals(old.getCompartmentId())||!pipeline.equals(old.getDeployPipelineId()))throw new IllegalArgumentException();
   var targets=new ArrayList<Target>();var notes=new ArrayList<String>();
   if(old.getDeployPipelineEnvironments()!=null&&old.getDeployPipelineEnvironments().getItems()!=null){
    var seen=new HashSet<String>();for(var ref:old.getDeployPipelineEnvironments().getItems()){
     if(!seen.add(ref.getDeployEnvironmentId()))continue;if(seen.size()>20){notes.add("환경이 20개를 넘어 일부만 표시합니다.");break;}
     try{var env=client.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(ref.getDeployEnvironmentId()).build()).getDeployEnvironment();
      if(!comp.equals(env.getCompartmentId())||!Objects.equals(old.getProjectId(),env.getProjectId())){notes.add("현재 범위 밖의 환경은 표시하지 않습니다.");continue;}
      if(env instanceof OkeClusterDeployEnvironment oke)targets.add(new Target(env.getId(),env.getDisplayName(),oke.getClusterId()));
     }catch(Exception e){notes.add("일부 환경을 조회하지 못했습니다. 환경 삭제·이동·조회 권한을 확인하세요.");}
    }
   }
   return new Context(old.getDisplayName(),List.copyOf(targets),notes.stream().distinct().toList());
  }catch(Exception e){throw new IllegalArgumentException("배포 실행과 사용 환경을 조회하지 못했습니다. 현재 범위와 OCI 조회 권한을 확인하세요.");}
 }
}
