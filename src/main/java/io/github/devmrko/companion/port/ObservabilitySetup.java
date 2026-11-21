package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

/** App-scoped configuration only. Provider UIs own telemetry storage and analysis. */
public interface ObservabilitySetup {
 record Settings(String provider,String dashboard,String endpoint,String service,String runtime,String agentId) {
  public Settings(String provider,String dashboard,String endpoint,String service,String runtime){this(provider,dashboard,endpoint,service,runtime,"");}
 }
 record Provider(String id,String name,String installation,String instrumentation,String note) {}
 List<Provider> providers();
 Settings get(Connection c,String region,String compartment,String app);
 void save(Connection c,String region,String compartment,String app,Settings settings);
 String instrumentation(Settings settings);
 java.util.Map<String,String> installationFiles(String provider);
}
