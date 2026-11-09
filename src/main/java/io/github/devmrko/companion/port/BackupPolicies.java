package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface BackupPolicies {
 record Schedule(String period,String type,String timing,String zone,String retention,boolean locked,boolean protectedFromDeletion) {}
 record Policy(String id,String name,boolean oracleDefined,String destination,List<Schedule> schedules) {}
 record Assignment(String id,String policy,String etag,String copyKey) {}
 record State(String volume,String name,String etag,String group,Assignment current,List<Policy> policies) {}
 record Plan(String operation,State state,Policy selected,String policyEtag) {}
 List<VolumeBackups.Volume> volumes(Connection c,String region,String compartment);
 List<Policy> policies(Connection c,String region,String compartment);
 State read(Connection c,String region,String compartment,String volume);
 Plan plan(Connection c,String region,String compartment,String volume,String operation,String policy);
 void apply(Connection c,String region,String compartment,Plan plan);
}
