package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface Registry {
    record Repository(String id,String kind,String name,String description,String state,Boolean immutable,boolean publicAccess,String etag) {}
    record Artifact(String id,String path,String version,String digest,String bytes,String state,String created) {}
    record Page(List<Artifact> items,String next) {}
    List<Repository> list(Connection c,String region,String compartment,String kind);
    Repository read(Connection c,String region,String compartment,String kind,String id);
    Page artifacts(Connection c,String region,String compartment,String kind,String id,String page);
    String create(Connection c,String region,String compartment,String kind,String name,String description,boolean immutable,String token);
    void update(Connection c,String region,String compartment,String kind,String id,String etag,String name,String description);
}
