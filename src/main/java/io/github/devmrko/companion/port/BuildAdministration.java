package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Choice;
import java.util.List;

/** Project/pipeline provisioning, separate from build execution. */
public interface BuildAdministration {
    record Project(String id,String name,String description,String state,String topic) {}
    List<Project> projects(Connection c,String region,String compartment);
    List<Choice> topics(Connection c,String region,String compartment);
    String createProject(Connection c,String region,String compartment,String name,String description,String topic,String token);
    String createPipeline(Connection c,String region,String compartment,String project,String name,String description,String token);
}
