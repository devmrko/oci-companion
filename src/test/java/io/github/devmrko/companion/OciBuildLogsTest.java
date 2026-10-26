package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.BuildRun;
import com.oracle.bmc.devops.responses.GetBuildRunResponse;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.Date;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OciBuildLogsTest {
 @Test void foreignRunNeverReachesLoggingClients() throws Exception {
  var c=new Connection("DEFAULT","tenant","user","region","label",false);
  var clients=mock(OciBuildHistory.class);var client=mock(DevopsClient.class);when(clients.client(c,"region")).thenReturn(client);
  when(client.getBuildRun(any())).thenReturn(GetBuildRunResponse.builder().buildRun(BuildRun.builder().id("run").compartmentId("foreign").buildPipelineId("pipeline").timeCreated(new Date()).build()).build());
  assertThrows(RuntimeException.class,()->new OciBuildLogs(clients).read(c,"region","comp","pipeline","run","",""));
  verify(clients,never()).authentication(any());
 }
}
