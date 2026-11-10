package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface DeploymentObservation {
 record Target(String environment,String name,String cluster){}
 record Context(String runName,List<Target> targets,List<String> notices){}
 Context read(Connection c,String region,String compartment,String pipeline,String run);
}
