package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.DeploymentTarget;
import io.github.devmrko.companion.service.Errors;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class VmTarget implements DeploymentTarget {
    public String id() { return "VM"; }
    public void validateStages(List<String> types) {
        var allowed=Set.of("COMPUTE_INSTANCE_GROUP_ROLLING_DEPLOYMENT", "MANUAL_APPROVAL", "WAIT");
        if (!types.contains("COMPUTE_INSTANCE_GROUP_ROLLING_DEPLOYMENT") || !types.stream().allMatch(allowed::contains))
            throw Errors.TARGET.exception();
    }
}
