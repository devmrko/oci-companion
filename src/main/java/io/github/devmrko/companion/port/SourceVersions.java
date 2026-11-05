package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Source;
import java.util.List;
public interface SourceVersions {
    record Ref(String kind,String name,String commit) {}
    List<Ref> refs(Connection c,String region,String compartment,Source source);
    List<Ref> commits(Connection c,String region,String compartment,Source source,String ref);
    boolean hasCommit(Connection c,String region,String compartment,Source source,String commit);
}
