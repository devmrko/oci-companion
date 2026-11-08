package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeWorkloadHealthTest {
 final JsonMapper json=JsonMapper.builder().build();
 final OkeWorkloadHealth.Target t=new OkeWorkloadHealth.Target("app","p","t","u","r","comp","ocid1.cluster.oc1.r.test","/unused","ns","app",null,null);
 final String dep="""
 {"kind":"Deployment","metadata":{"name":"app","namespace":"ns","uid":"dep","generation":2},"spec":{"replicas":1},"status":{"observedGeneration":2,"replicas":1,"readyReplicas":1,"availableReplicas":1,"updatedReplicas":1}}
 """;
 final String rs="""
 {"items":[{"metadata":{"namespace":"ns","uid":"rs","ownerReferences":[{"kind":"Deployment","uid":"dep","controller":true}]}}]}
 """;
 String pod(String name,String owner,boolean ready){return """
 {"metadata":{"name":"%s","namespace":"ns","ownerReferences":[{"kind":"ReplicaSet","uid":"%s","controller":true}]},"status":{"phase":"Running","conditions":[{"type":"Ready","status":"%s"}],"containerStatuses":[{"restartCount":2,"state":{"waiting":{"reason":"CrashLoopBackOff"}}}]}}
 """.formatted(name,owner,ready?"True":"False");}
 OkeWorkloadHealth.Status summarize(String d,String pods){return OkeWorkloadHealth.summarize(t,json.readTree(d),json.readTree(rs),json.readTree("{\"items\":["+pods+"]}"));}
 @Test void onlyOwnedPodsAreShown(){var result=summarize(dep,pod("mine","rs",true)+","+pod("other","unrelated",false));assertEquals("준비 완료",result.state());assertEquals(1,result.pods().size());assertEquals("mine",result.pods().getFirst().name());assertEquals(2,result.pods().getFirst().restarts());}
 @Test void unreadyPodIsNotHealthy(){var result=summarize(dep,pod("mine","rs",false));assertNotEquals("준비 완료",result.state());assertEquals("CrashLoopBackOff",result.pods().getFirst().reason());}
 @Test void unobservedGenerationAndScaleZeroAreDistinct(){assertEquals("변경 반영 대기",summarize(dep.replace("observedGeneration\":2","observedGeneration\":1"),pod("mine","rs",true)).state());assertEquals("복제본 0개",summarize(dep.replace("\"replicas\":1","\"replicas\":0"),"").state());}
 @Test void staleReplicaCountCannotClaimReady(){assertNotEquals("준비 완료",summarize(dep.replace("updatedReplicas\":1","updatedReplicas\":0"),pod("mine","rs",true)).state());assertNotEquals("준비 완료",summarize(dep,"").state());}
 @Test void wrongDeploymentRejected(){assertThrows(IllegalArgumentException.class,()->summarize(dep.replace("\"name\":\"app\"","\"name\":\"other\""),""));}
 @TempDir Path temp;
 @Test void scopeFailureNeverRunsKubectl()throws Exception {
  var c=new Connection("p","t","u","r","name",false);var registry=mock(AgentConnectionRegistry.class);var cloud=mock(OciAgentDependencies.class);
  when(registry.get(c,"r","comp","app")).thenThrow(new IllegalArgumentException());
  var svc=spy(new OkeWorkloadHealth(temp.resolve("missing").toString(),"/unused",registry,cloud));
  assertEquals("조회 불가",svc.check(c,"r","comp","app").state());verifyNoInteractions(cloud);verify(svc,never()).read(any(),anyString(),any());
 }
 @Test void failedReadDoesNotKeepReady()throws Exception {
  var kube=temp.resolve("config");Files.writeString(kube,"trusted test fixture");var file=temp.resolve("targets.json");
  var target=new OkeWorkloadHealth.Target("app","p","t","u","r","comp",t.clusterId(),kube.toString(),"ns","app",null,null);Files.writeString(file,json.writeValueAsString(List.of(target)));
  var c=new Connection("p","t","u","r","name",false);var registry=mock(AgentConnectionRegistry.class);var cloud=mock(OciAgentDependencies.class);var ref=new AgentConnectionRegistry.Dependency("OKE","cluster",t.clusterId());
  var app=new AgentConnectionRegistry.Registration("app","a","oke","monitoring-http-v1","http://127.0.0.1:1","ref","p","t","u","r","comp",List.of(ref));
  when(registry.get(c,"r","comp","app")).thenReturn(app);when(cloud.check(c,"r","comp",ref)).thenReturn(new OciAgentDependencies.Status(ref,"ACTIVE","now","",true));
  var svc=spy(new OkeWorkloadHealth(file.toString(),"/unused",registry,cloud));doReturn(json.readTree(dep)).when(svc).read(target,"deployment","app");doReturn(json.readTree(rs)).when(svc).read(target,"replicasets",null);doReturn(json.readTree("{\"items\":["+pod("mine","rs",true)+"]}")).when(svc).read(target,"pods",null);
  assertEquals("준비 완료",svc.check(c,"r","comp","app").state());doThrow(new RuntimeException("private error")).when(svc).read(target,"pods",null);
  var failed=svc.check(c,"r","comp","app");assertEquals("조회 불가",failed.state());assertTrue(failed.pods().isEmpty());assertFalse(failed.toString().contains("private error"));
 }
}
