package io.github.devmrko.companion.domain;

import java.time.Instant;
import java.io.Serializable;

public final class Models {
    private Models() {}
    public record Connection(String profile, String tenancy, String user, String region,
                             String label, boolean demo) implements Serializable {
        public String scope() { return (demo ? "demo:" : "oci:") + tenancy + ":" + user + ":" + region; }
    }
    public record Workspace(String id, String name, String repositoryUrl, String branch,
                            String recipe, String compartmentId, String buildPipelineId,
                            String deployPipelineId, Instant createdAt) {}
    public record Pipeline(String id, String name, String kind) {}
    public record Run(String id, String workspaceId, String kind, String pipelineId,
                      String remoteId, String status, Instant createdAt, Instant updatedAt) {}
    public record RemoteRun(String id, String status) {}
    public record Guide(String id, String title, String description, java.util.List<String> steps,
                        String cli, String sdk, String permissions, String url) {}
}
