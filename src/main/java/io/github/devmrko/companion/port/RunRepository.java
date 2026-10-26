package io.github.devmrko.companion.port;
import java.util.*;
import io.github.devmrko.companion.domain.Models.Run;
public interface RunRepository {
    List<Run> list(String scope, String workspaceId);
    Optional<Run> find(String scope, String id);
    boolean reserve(String scope, Run run);
    void update(String scope, String id, String remoteId, String status);
}
