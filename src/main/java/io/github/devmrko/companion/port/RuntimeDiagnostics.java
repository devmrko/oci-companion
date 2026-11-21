package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.time.Instant;
import java.util.List;
/** App-scoped runtime observations; implementations use the runtime's native diagnostics. */
public interface RuntimeDiagnostics {
 record Capability(String name,String state,String tool,String note) {}
 record Metric(String name,String statistic,double value,String unit,Instant observedAt) {}
 record Snapshot(String app,String runtime,Instant checkedAt,List<Capability> capabilities,List<Metric> metrics) {}
 record Dump(String app,Instant capturedAt,String text,int threads) {}
 Snapshot inspect(Connection c,String region,String compartment,String app);
 Dump threadDump(Connection c,String region,String compartment,String app);
}
