package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface OkeDeploymentSetup {
 record Input(String environment,String repository,String image,String name,String description,String namespace,String app,String architecture,int replicas,int cpuMillis,int memoryMi,int port,String healthPath,String variables,String pullSecret,String claim,String mountPath,boolean rollback,Long fsGroup) {
  public Input(String environment,String repository,String image,String name,String description,String namespace,String app,String architecture,int replicas,int cpuMillis,int memoryMi,int port,String healthPath,String variables,String pullSecret,String claim,String mountPath,boolean rollback){this(environment,repository,image,name,description,namespace,app,architecture,replicas,cpuMillis,memoryMi,port,healthPath,variables,pullSecret,claim,mountPath,rollback,null);}
 }
 record Preview(Input input,String project,String environmentName,String imageUrl,String yaml,String fingerprint) {}
 Preview preview(Connection c,String region,String compartment,Input input);
 DeploymentSetup.Result create(Connection c,String region,String compartment,Preview preview,String token);
}
