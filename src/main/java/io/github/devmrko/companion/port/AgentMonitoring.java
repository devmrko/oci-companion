package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

public interface AgentMonitoring {
    record Trace(String requestId, String status, String version, String mode, String model,
                 String region, String inputTokens, String outputTokens, String elapsedMs,
                 String tools, String errorCode) {}
    record Capability(String name, String status, String health) {}
    record Snapshot(String name, String status, String version, List<Trace> traces, String notice,
                    List<Capability> capabilities, String capabilityNotice) {
        public Snapshot(String name, String status, String version, List<Trace> traces, String notice) {
            this(name,status,version,traces,notice,List.of(),"기능 상태를 확인하지 못했습니다.");
        }
    }
    Snapshot snapshot(Connection connection, String region, String compartment);
}
