package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

public interface PipelineIam {
    record Check(boolean inspected, boolean root, String groupName, boolean createGroup,
                 String rule, List<String> missing, List<String> warnings, String error) {
        public boolean ready() { return inspected && missing.isEmpty(); }
    }
    Check inspect(Connection c, String region, String compartment, String pipeline);
    void create(Connection c, String region, String compartment, String pipeline, Check preview, String token);
}
