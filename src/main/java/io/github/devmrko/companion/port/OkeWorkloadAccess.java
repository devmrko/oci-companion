package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.time.Instant;
import java.util.List;
public interface OkeWorkloadAccess {
 record Input(String environment,String endpoint,String namespace,String serviceAccount,String bucket) {}
 record Options(String cluster,String clusterType,String objectNamespace,List<String> accounts,List<String> buckets) {}
 record Plan(Input input,String cluster,String policy,String manifest,String job,String owner) {}
 record Result(Instant observedAt,String state,int httpStatus,String note) {}
 Options options(Connection c,String region,String compartment,String environment,String endpoint,String namespace);
 Plan prepare(Connection c,String region,String compartment,Input input);
 String execute(Connection c,String region,String compartment,Plan plan);
 Result result(Connection c,String region,String compartment,Plan plan);
 void remove(Connection c,String region,String compartment,Plan plan);
}
