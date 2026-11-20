package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OciBuildInsightsTest {
 @Test void nativeSummaryTimesAreNotTimeUpdated(){
  var start=Date.from(java.time.Instant.parse("2026-11-01T00:00:00Z"));var end=new Date(start.getTime()+120000);
  var r=OciBuildHistory.summary(BuildRunSummary.builder().id("r").lifecycleState(BuildRun.LifecycleState.Succeeded).timeCreated(start).timeUpdated(new Date(end.getTime()+999999)).buildRunProgressSummary(BuildRunProgressSummary.builder().timeStarted(start).timeFinished(end).build()).build());
  assertEquals(end.toInstant().toString(),r.finishedAt());assertEquals("2분 0초",io.github.devmrko.companion.service.BuildInsights.duration(r.startedAt(),r.finishedAt()));
 }
 @Test void stageAndCommandTimesAreMappedFromNativeRunAndCrossScopeRejected() throws Exception {
  var client=mock(DevopsClient.class);var adapter=spy(new OciBuildHistory("unused"));var c=new Connection("DEFAULT","tenant","user","region","label",false);doReturn(client).when(adapter).client(c,"region");
  var start=Date.from(java.time.Instant.parse("2026-11-01T00:00:00Z"));var end=new Date(start.getTime()+65000);
  var stage=BuildStageRunProgress.builder().buildPipelineStageId("stage").stageDisplayName("build").timeStarted(start).timeFinished(end).actualBuildRunnerShape("VM.Standard.E4.Flex").buildSpecFile("build.yaml").steps(List.of(BuildStageRunStep.builder().name("tests").timeStarted(start).timeFinished(end).build())).build();
  var nativeRun=BuildRun.builder().id("run").compartmentId("comp").buildPipelineId("pipeline").lifecycleState(BuildRun.LifecycleState.Succeeded).buildRunProgress(BuildRunProgress.builder().buildPipelineStageRunProgress(Map.of("stage",stage)).build()).build();
  when(client.getBuildRun(any())).thenReturn(GetBuildRunResponse.builder().buildRun(nativeRun).build());
  var detail=adapter.detail(c,"region","comp","pipeline","run");assertEquals("1분 5초",detail.steps().getFirst().duration());assertEquals("COMMAND",detail.steps().get(1).type());assertEquals("stage",detail.steps().get(1).stageId());
  assertTrue(adapter.executionContext(c,"region","comp","pipeline","run").containsValue("VM.Standard.E4.Flex"));
  assertThrows(RuntimeException.class,()->adapter.detail(c,"region","foreign","pipeline","run"));assertThrows(RuntimeException.class,()->adapter.executionContext(c,"region","comp","other","run"));
 }
}
