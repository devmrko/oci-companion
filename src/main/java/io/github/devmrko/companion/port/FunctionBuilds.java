package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

public interface FunctionBuilds {
    String CONTRACT="maven-v1";
    record Application(String id,String name,String state,String shape) {}
    record Builder(String id,String name,String state,String image,long memory,int timeout,
                   String contract,String namespace,String bucket,String prefix,String etag) {
        public boolean compatible() { return CONTRACT.equals(contract) && !namespace.isBlank() && !bucket.isBlank() && prefix.matches("[a-zA-Z0-9_-]{1,80}"); }
    }
    record Run(String id,String status,String result,String createdAt) {}
    record Runs(List<Run> items,String next) {}
    record Storage(String namespace,List<String> buckets) {}
    record Subnet(String id,String name,String cidr) {}
    List<Subnet> subnets(Connection connection,String region,String compartment);
    Storage storageOptions(Connection connection,String region,String compartment);
    List<Application> applications(Connection connection,String region,String compartment);
    List<Builder> builders(Connection connection,String region,String compartment,String application);
    Builder read(Connection connection,String region,String compartment,String application,String function);
    void invoke(Connection connection,String region,String compartment,String application,String function,String etag,String payload);
    Runs runs(Connection connection,String region,String compartment,String application,String function,String start);
    Run run(Connection connection,String region,String compartment,String application,String function,String run);
    String log(Connection connection,String region,String compartment,String application,String function,String run);
}
