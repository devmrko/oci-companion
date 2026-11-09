package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface OkePreparation {
 record Cluster(String id,String name,String state,String version) {}
 record Pool(String name,String state,String shape,String cpu,String memory,String bootGb,String size) {}
 record Network(String id,String name) {}
 record Detail(Cluster cluster,boolean privateEndpoint,String vcn,List<Pool> pools,List<Network> subnets,List<Network> nsgs,List<String> notices) {}
 List<Cluster> clusters(Connection c,String region,String compartment);
 Detail read(Connection c,String region,String compartment,String cluster);
 String create(Connection c,String region,String compartment,String cluster,String project,String name,String description,String subnet,List<String> nsgs,String token);
}
