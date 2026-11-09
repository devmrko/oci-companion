package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface DeploymentRollback {
 record Plan(String pipeline,String run,String runName,String stage,String environment,String targetType,String fingerprint) {}
 Plan plan(Connection c,String region,String compartment,String pipeline,String run);
 String execute(Connection c,String region,String compartment,Plan expected,String token);
}
