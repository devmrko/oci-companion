package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface OkeWorkloadConnections {
 record Cluster(String id,String name,String state){}
 record Settings(String id,String name,String environment,String cluster,String endpoint,String namespace,String deployment,boolean inspectNodes){}
 List<Cluster> clusters(Connection c,String region,String compartment);
 List<String> namespaces(Connection c,String region,String compartment,String cluster,String endpoint);
 List<String> deployments(Connection c,String region,String compartment,String cluster,String endpoint,String namespace);
 Settings current(Connection c,String region,String compartment,String id);
 String save(Connection c,String region,String compartment,Settings settings);
}
