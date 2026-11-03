package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Choice;
import java.util.List;
public interface BuildProjectResources {
    record Repository(String id,String name,String url,String branch,String state) {}
    record Log(String id,String group,String name,boolean enabled,String state) {}
    record Resources(List<Repository> repositories,List<Choice> groups,List<Log> logs) {}
    Resources read(Connection c,String region,String compartment,String project);
    String createRepository(Connection c,String region,String compartment,String project,String name,String description,String token);
    String createLogGroup(Connection c,String region,String compartment,String project,String name,String token);
    void createLog(Connection c,String region,String compartment,String project,String group,String name,String token);
}
