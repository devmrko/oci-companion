package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Source;
public interface GitSpecifications {
    record Target(Source source,String branch,String path,String username,String secret) {}
    record File(String revision,String content) {}
    File read(Connection c,String region,Target target);
    String save(Connection c,String region,Target target,String expectedRevision,String content,String message);
}
