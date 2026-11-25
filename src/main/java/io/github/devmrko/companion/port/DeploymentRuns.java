package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface DeploymentRuns {
    record Pipeline(String id,String name,String state) {}
    record Job(String name,String serviceAccount,int deadlineSeconds,int retries,int ttlSeconds,List<String> secrets) {}
    record Plan(String pipeline,String name,String environment,List<String> instances,String fingerprint,
                String targetType,String cluster,String namespace,List<String> images,java.util.Map<String,List<String>> imageArchitectures,List<Job> jobs) {
        public Plan(String pipeline,String name,String environment,List<String> instances,String fingerprint,String targetType,String cluster,String namespace,List<String> images,java.util.Map<String,List<String>> imageArchitectures){
            this(pipeline,name,environment,instances,fingerprint,targetType,cluster,namespace,images,imageArchitectures,List.of());
        }
        public Plan(String pipeline,String name,String environment,List<String> instances,String fingerprint,String targetType,String cluster,String namespace,List<String> images,List<Job> jobs){
            this(pipeline,name,environment,instances,fingerprint,targetType,cluster,namespace,images,java.util.Map.of(),jobs);
        }
        public Plan(String pipeline,String name,String environment,List<String> instances,String fingerprint,String targetType,String cluster,String namespace,List<String> images){
            this(pipeline,name,environment,instances,fingerprint,targetType,cluster,namespace,images,java.util.Map.of(),List.of());
        }
        public Plan(String pipeline,String name,String environment,List<String> instances,String fingerprint){
            this(pipeline,name,environment,instances,fingerprint,"VM","","",List.of());
        }
    }
    record Step(String stage,String target,String name,String state) {}
    record Run(String id,String name,String state,String detail,List<Step> steps,String cloudState,List<DeploymentImageVerification.Image> images) {
        public Run(String id,String name,String state,String detail,List<Step> steps){this(id,name,state,detail,steps,state,List.of());}
        public Run(String id,String name,String state,String detail){this(id,name,state,detail,List.of());}
    }
    record History(List<Run> items,String next) {}
    History history(Connection c,String region,String compartment,String pipeline,String page);
    List<Pipeline> pipelines(Connection c,String region,String compartment);
    Plan plan(Connection c,String region,String compartment,String pipeline);
    String start(Connection c,String region,String compartment,String pipeline,String fingerprint,String token);
    Run run(Connection c,String region,String compartment,String pipeline,String id);
}
