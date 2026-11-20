package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.time.Instant;
import java.util.*;
/** Native namespace inventory. No ConfigMap values, Secret data, tokens or certificates in results. */
public interface OkeRuntimeResources {
 record Resource(String name,String type) {}
 record Listing(boolean available,List<Resource> items,String note) {}
 record Route(String host,String path,String service,String port) {}
 record Tls(List<String> hosts,String secret) {}
 record Ingress(String name,String controller,List<Route> routes,List<Tls> tls,List<String> addresses) {}
 record Service(String name,String type,List<String> addresses) {}
 record Snapshot(Instant observedAt,String cluster,String namespace,Map<String,Listing> resources,List<Ingress> ingresses,List<Service> services) {}
 Snapshot read(Connection connection,String region,String compartment,String environment,String endpoint,String namespace);
}
