package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface DeploymentRuns {
    record Pipeline(String id,String name,String state) {}
    record Plan(String pipeline,String name,String environment,List<String> instances,String fingerprint,
                String targetType,String cluster,String namespace,List<String> images) {
        public Plan(String pipeline,String name,String environment,List<String> instances,String fingerprint){
            this(pipeline,name,environment,instances,fingerprint,"VM","","",List.of());
        }
    }
    record Run(String id,String name,String state,String detail) {}
    List<Pipeline> pipelines(Connection c,String region,String compartment);
    Plan plan(Connection c,String region,String compartment,String pipeline);
    String start(Connection c,String region,String compartment,String pipeline,String fingerprint,String token);
    Run run(Connection c,String region,String compartment,String pipeline,String id);
}
