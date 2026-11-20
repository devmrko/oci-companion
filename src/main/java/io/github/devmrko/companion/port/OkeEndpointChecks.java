package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.time.Instant;
import java.util.List;
/** Read-only probe from the app server; no credentials, redirects or response bodies. */
public interface OkeEndpointChecks {
 record Check(String item,String state,String detail) {}
 record Report(Instant observedAt,String target,List<Check> checks) {}
 Report inspect(Connection c,String region,String compartment,String environment,String endpoint,String namespace,String ingress,String host,String path);
}
