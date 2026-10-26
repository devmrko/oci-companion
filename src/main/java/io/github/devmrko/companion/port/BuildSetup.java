package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Choice;
import java.util.List;
public interface BuildSetup {
    record EditableStage(String id,String etag,String name,String spec,int timeout,String image,String source,String branch) {}
    record Artifact(String id,String name,String type,String location) {}
    record Mapping(String output,String artifact) {}
    record Delivery(String id,String etag,String name,List<Mapping> mappings) {}
    record Settings(String etag,String name,String description,List<EditableStage> stages,List<Artifact> artifacts,List<Choice> repositories,List<String> warnings,List<Delivery> deliveries) {
        public Settings(String etag,String name,String description,List<EditableStage> stages,List<Artifact> artifacts,List<Choice> repositories,List<String> warnings) {
            this(etag,name,description,stages,artifacts,repositories,warnings,List.of());
        }
    }
    Settings read(Connection c,String region,String compartment,String pipeline);
    void rename(Connection c,String region,String compartment,String pipeline,String etag,String name,String description);
    void updateBuild(Connection c,String region,String compartment,String pipeline,String stage,String etag,String name,String spec,int timeout,String branch);
    void updateDelivery(Connection c,String region,String compartment,String pipeline,String stage,String etag,String output,String artifact);
    String createRepository(Connection c,String region,String compartment,String name,String token);
    String createArtifact(Connection c,String region,String compartment,String pipeline,String name,String type,String repository,String path,String version,String token);
    String deliver(Connection c,String region,String compartment,String pipeline,String revision,String predecessor,String output,String artifact,String token);
}
