package io.github.devmrko.companion.port;
import java.util.List;
import io.github.devmrko.companion.domain.Models.*;
public interface PipelineGateway {
    List<Pipeline> pipelines(Connection connection, String compartmentId);
    void validate(Connection connection, Workspace workspace, String kind);
    RemoteRun start(Connection connection, String kind, String pipelineId, String requestToken);
    RemoteRun status(Connection connection, String kind, String remoteId);
}
