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
 record PullRegistry(boolean available,List<String> hosts,String note) {}
 default PullRegistry pullRegistry(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String secret){return new PullRegistry(false,List.of(),"pull Secret 레지스트리 조회 미지원");}
 record AllocatedPort(String namespace,String service,int port){}
 record NodePorts(boolean available,List<AllocatedPort> ports){}
 default NodePorts nodePorts(Connection c,String region,String compartment,String environment,String endpoint,String namespace){return new NodePorts(false,List.of());}
 default RegistryCredentials pullCredentials(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String secret,String host){throw io.github.devmrko.companion.service.Errors.DEPLOYMENT_RUNTIME.exception();}
 default String deployment(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String name){throw io.github.devmrko.companion.service.Errors.DEPLOYMENT_RUNTIME.exception();}
 default DeploymentImageVerification.Result verifyImages(Connection c,String region,String compartment,String environment,String endpoint,DeploymentImageVerification.Expected expected){return DeploymentImageVerification.Result.unknown();}
 Snapshot read(Connection connection,String region,String compartment,String environment,String endpoint,String namespace);
}
