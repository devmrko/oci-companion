package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface OkeDeploymentSetup {
 record Input(String environment,String repository,String image,String name,String description,String namespace,String app,String architecture,int replicas,int cpuMillis,int memoryMi,int port,String healthPath,String variables,String pullSecret,String claim,String mountPath,boolean rollback,Long fsGroup,String configMap,String secret,String serviceAccount,String startupPath,String livenessPath,Integer terminationSeconds) {
  public Input(String environment,String repository,String image,String name,String description,String namespace,String app,String architecture,int replicas,int cpuMillis,int memoryMi,int port,String healthPath,String variables,String pullSecret,String claim,String mountPath,boolean rollback,Long fsGroup){this(environment,repository,image,name,description,namespace,app,architecture,replicas,cpuMillis,memoryMi,port,healthPath,variables,pullSecret,claim,mountPath,rollback,fsGroup,"","","","","",30);}
  public Input { configMap=configMap==null?"":configMap;secret=secret==null?"":secret;serviceAccount=serviceAccount==null?"":serviceAccount;startupPath=startupPath==null?"":startupPath;livenessPath=livenessPath==null?"":livenessPath;terminationSeconds=terminationSeconds==null?30:terminationSeconds; }
  public Input(String environment,String repository,String image,String name,String description,String namespace,String app,String architecture,int replicas,int cpuMillis,int memoryMi,int port,String healthPath,String variables,String pullSecret,String claim,String mountPath,boolean rollback){this(environment,repository,image,name,description,namespace,app,architecture,replicas,cpuMillis,memoryMi,port,healthPath,variables,pullSecret,claim,mountPath,rollback,null);}
 }
 record Preview(Input input,String project,String environmentName,String imageUrl,String yaml,String fingerprint) {}
 Preview preview(Connection c,String region,String compartment,Input input);
 DeploymentSetup.Result create(Connection c,String region,String compartment,Preview preview,String token);
}
