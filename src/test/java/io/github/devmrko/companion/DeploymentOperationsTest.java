package io.github.devmrko.companion.service;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.domain.Models.Connection;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeploymentOperationsTest {
 final DeploymentRuns runs=mock(DeploymentRuns.class);final BuildHandoff handoff=mock(BuildHandoff.class);
 final DeploymentImages images=mock(DeploymentImages.class);final DeploymentArchitectures architectures=mock(DeploymentArchitectures.class);
 final DeploymentOperations.Context context=new DeploymentOperations.Context(new Connection("DEFAULT","tenancy","user","region","test",false),"region","comp","interaction");
 final DeploymentOperations.Target target=new DeploymentOperations.Target("pipeline","","");
 final DeploymentRuns.Plan plan=new DeploymentRuns.Plan("pipeline","app","environment",List.of("vm"),"fingerprint");
 final class Time extends Clock { Instant now=Instant.parse("2026-11-21T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;} }
 final Time time=new Time();
 DefaultDeploymentOperations service(){
  when(runs.plan(context.connection(),"region","comp","pipeline")).thenReturn(plan);
  when(images.inspect(any(),anyString(),anyString(),anyList())).thenReturn(List.of());when(architectures.inspect(any(),anyString(),any())).thenReturn(List.of());
  when(runs.start(any(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn("run");
  return new DefaultDeploymentOperations(runs,handoff,mock(DeploymentLogs.class),images,architectures,time);
 }
 @Test void nonHttpCallerUsesSameChecksAndOneTimeExecution(){
  var s=service();var inspected=s.inspect(context,target);assertTrue(inspected.executable());assertThrows(RuntimeException.class,()->s.execute(context,"invented-plan"));
  var p=s.prepare(context,target);assertEquals(time.now,p.assessment().observedAt());assertEquals("run",s.execute(context,p.id()).id());
  assertThrows(RuntimeException.class,()->s.execute(context,p.id()));verify(runs,times(1)).start(any(),anyString(),anyString(),anyString(),anyString(),eq(p.id()));
 }
 @Test void foreignContextCannotExecuteOrConsumePlan(){
  var s=service();var p=s.prepare(context,target);var other=new DeploymentOperations.Context(context.connection(),"region","other-comp","interaction");
  assertThrows(RuntimeException.class,()->s.execute(other,p.id()));
  var otherSession=new DeploymentOperations.Context(context.connection(),"region","comp","other-session");assertThrows(RuntimeException.class,()->s.execute(otherSession,p.id()));
  assertEquals("run",s.execute(context,p.id()).id());
 }
 @Test void expiredAndChangedPlansNeverStart(){
  var s=service();var p=s.prepare(context,target);time.now=time.now.plusSeconds(600);assertThrows(RuntimeException.class,()->s.execute(context,p.id()));
  var next=s.prepare(context,target);when(runs.plan(any(),anyString(),anyString(),anyString())).thenReturn(new DeploymentRuns.Plan("pipeline","app","environment",List.of("vm"),"changed"));
  assertThrows(RuntimeException.class,()->s.execute(context,next.id()));verify(runs,never()).start(any(),anyString(),anyString(),anyString(),anyString(),anyString());
 }
 @Test void blockedImageHasStructuredIssueWithoutExecutionToken(){
  var s=service();when(images.inspect(any(),anyString(),anyString(),anyList())).thenReturn(List.of(new DeploymentImages.Check("image",DeploymentImages.State.BLOCKED,"missing")));
  var p=s.prepare(context,target);assertEquals("",p.id());assertFalse(p.assessment().executable());assertEquals(Errors.DEPLOYMENT_IMAGE.code(),p.assessment().issues().getFirst().code());
  verify(runs,never()).start(any(),anyString(),anyString(),anyString(),anyString(),anyString());
 }
}
