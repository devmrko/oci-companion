package io.github.devmrko.companion.port;
import java.util.List;
public interface DeploymentTarget {
    String id();
    void validateStages(List<String> stageTypes);
}
