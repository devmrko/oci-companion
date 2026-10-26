package io.github.devmrko.companion.port;
import java.util.*;
import io.github.devmrko.companion.domain.Models.Workspace;
public interface WorkspaceRepository {
    List<Workspace> list(String scope);
    Optional<Workspace> find(String scope, String id);
    void save(String scope, Workspace workspace);
}
