package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface BuildHandoff {
    record Proof(String buildRun,String fingerprint,int packages) {}
    Proof verify(Connection c,String region,String compartment,String buildPipeline,String buildRun,String deployPipeline);
}
