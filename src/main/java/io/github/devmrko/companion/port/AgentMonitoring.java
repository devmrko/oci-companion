package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

public interface AgentMonitoring {
    record Trace(String requestId, String status, String version, String mode, String model,
                 String region, String inputTokens, String outputTokens, String elapsedMs,
                 String tools, String errorCode) {}
    record Snapshot(String name, String status, String version, List<Trace> traces, String notice) {}
    Snapshot snapshot(Connection connection, String region, String compartment);
}
