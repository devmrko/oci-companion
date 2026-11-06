package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.Project;
import com.oracle.bmc.devops.responses.GetProjectResponse;
import com.oracle.bmc.logging.LoggingManagementClient;
import com.oracle.bmc.logging.model.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildProjectResources.LogReadiness;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OciBuildLogReadinessTest {
 final Connection c=new Connection("DEFAULT","tenant","user","region","label",false);
 final OciBuildHistory clients=mock(OciBuildHistory.class);
 final DevopsClient devops=mock(DevopsClient.class);
 final LoggingManagementClient logging=mock(LoggingManagementClient.class,RETURNS_DEEP_STUBS);
 OciBuildProjectResources adapter() throws Exception {
  when(clients.client(c,"region")).thenReturn(devops);
  when(devops.getProject(any())).thenReturn(GetProjectResponse.builder().project(Project.builder().id("project").compartmentId("comp").lifecycleState(Project.LifecycleState.Active).build()).build());
  when(logging.getPaginators().listLogGroupsRecordIterator(any())).thenReturn(List.of(LogGroupSummary.builder().id("group").build()));
  return new OciBuildProjectResources(clients){protected LoggingManagementClient logging(Connection c,String region){return logging;}};
 }
 LogSummary log(String project,boolean enabled,LogLifecycleState state){return LogSummary.builder().id("log").isEnabled(enabled).lifecycleState(state).configuration(Configuration.builder().source(OciService.builder().service("devops").resource(project).category("all").build()).build()).build();}
 @Test void distinguishesReadyDisabledMissingAndForeignLogs() throws Exception {
  var a=adapter();
  when(logging.getPaginators().listLogsRecordIterator(any())).thenReturn(List.of(log("project",true,LogLifecycleState.Active)));
  assertEquals(LogReadiness.READY,a.logReadiness(c,"region","comp","project"));
  when(logging.getPaginators().listLogsRecordIterator(any())).thenReturn(List.of(log("project",false,LogLifecycleState.Active)));
  assertEquals(LogReadiness.DISABLED,a.logReadiness(c,"region","comp","project"));
  when(logging.getPaginators().listLogsRecordIterator(any())).thenReturn(List.of(log("other",true,LogLifecycleState.Active)));
  assertEquals(LogReadiness.NOT_FOUND,a.logReadiness(c,"region","comp","project"));
  when(logging.getPaginators().listLogsRecordIterator(any())).thenReturn(List.of());
  assertEquals(LogReadiness.NOT_FOUND,a.logReadiness(c,"region","comp","project"));
 }
 @Test void deniedLookupIsUnknownNotMissing() throws Exception {
  var a=adapter();
  when(logging.getPaginators().listLogsRecordIterator(any())).thenThrow(new com.oracle.bmc.model.BmcException(403,"NotAuthorized","private diagnostic","request"));
  assertEquals(LogReadiness.UNKNOWN,a.logReadiness(c,"region","comp","project"));
 }
 @Test void foreignProjectNeverReadsLogs() throws Exception {
  var a=adapter();clearInvocations(logging);
  assertThrows(RuntimeException.class,()->a.logReadiness(c,"region","foreign","project"));
  verify(logging,never()).getPaginators();
 }
}
