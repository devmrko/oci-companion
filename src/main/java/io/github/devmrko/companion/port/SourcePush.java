package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface SourcePush {
 record Plan(GitSpecifications.Target source,GitSpecifications.Target destination,String revision){}
 Plan preview(Connection c,String region,GitSpecifications.Target source,GitSpecifications.Target destination);
 String push(Connection c,String region,Plan plan);
}
