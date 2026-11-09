package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface VolumeBackups {
 record Volume(String id,String name,String state,long size,String domain) {}
 record Backup(String id,String name,String state,long size,String volume,String type,String created) {}
 record Catalog(List<Volume> volumes,List<Backup> backups,List<String> domains) {}
 record Input(String operation,String target,String name,String type,String domain) {}
 record Plan(Input input,String targetName,long size,String etag) {}
 Catalog list(Connection c,String region,String compartment);
 Plan plan(Connection c,String region,String compartment,Input input);
 String apply(Connection c,String region,String compartment,Plan plan,String retryToken);
}
