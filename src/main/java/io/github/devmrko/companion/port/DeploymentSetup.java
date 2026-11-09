package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface DeploymentSetup {
    record Input(String environment,String repository,String artifact,String name,String description,String destination,String runAs,String stop,String install,String start,String health,String recovery,String variables,String vaultVariables,int timeout,int batch) {
        public Input {
            environment=java.util.Objects.toString(environment,"");repository=java.util.Objects.toString(repository,"");artifact=java.util.Objects.toString(artifact,"");
            name=java.util.Objects.toString(name,"");description=java.util.Objects.toString(description,"");destination=java.util.Objects.toString(destination,"");runAs=java.util.Objects.toString(runAs,"");
            stop=java.util.Objects.toString(stop,"");install=java.util.Objects.toString(install,"");start=java.util.Objects.toString(start,"");health=java.util.Objects.toString(health,"");recovery=java.util.Objects.toString(recovery,"");variables=java.util.Objects.toString(variables,"");vaultVariables=java.util.Objects.toString(vaultVariables,"");
        }
    }
    record Preview(Input input,String project,String environmentName,String artifactPath,String version,String sha256,String bytes,String yaml,String fingerprint) {}
    record Result(String pipeline,String packageReference,String specification,String stage,String error) {public boolean complete(){return error.isEmpty()&&!stage.isEmpty();}}
    Preview preview(Connection connection,String region,String compartment,Input input);
    Result create(Connection connection,String region,String compartment,Preview preview,String token);
}
