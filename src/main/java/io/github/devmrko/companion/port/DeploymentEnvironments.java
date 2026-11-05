package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

/** Native deployment environments; implementations own provider-specific preparation. */
public interface DeploymentEnvironments {
    record Vm(String id,String name,String state,String shape) {}
    record Environment(String id,String name,String description,String state,String project,List<String> instances,List<String> queries) {}
    record Check(String name,String state,String detail) {}
    record Inspection(Vm vm,List<Check> checks) {}
    List<Vm> instances(Connection c,String region,String compartment);
    List<Environment> environments(Connection c,String region,String compartment);
    Environment environment(Connection c,String region,String compartment,String id);
    Inspection inspect(Connection c,String region,String compartment,String instance);
    String create(Connection c,String region,String compartment,String project,String name,String description,List<String> instances,String token);
}
