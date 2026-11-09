package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface DeploymentLogs {
    BuildLogs.Page read(Connection c,String region,String compartment,String pipeline,String run,String page,String until);
}
