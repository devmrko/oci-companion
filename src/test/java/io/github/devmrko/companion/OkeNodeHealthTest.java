package io.github.devmrko.companion.adapter;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeNodeHealthTest {
 final JsonMapper json=JsonMapper.builder().build();
 final OkeWorkloadHealth.Target enabled=new OkeWorkloadHealth.Target("app","p","t","u","r","comp","cluster","/unused","ns","app",null,null,true);
 final String node="""
 {"kind":"Node","metadata":{"name":"worker-1"},"spec":{"unschedulable":true},"status":{"conditions":[{"type":"Ready","status":"False"},{"type":"DiskPressure","status":"True"},{"type":"MemoryPressure","status":"False"}]}}
 """;
 OkeWorkloadHealth svc(){return spy(new OkeWorkloadHealth("/unused","/unused",mock(AgentConnectionRegistry.class),mock(OciAgentDependencies.class)));}
 OkeWorkloadHealth.Pod pod(String name,String node){return new OkeWorkloadHealth.Pod(name,"Running","Ready",0,"",node);}
 @Test void conditionMeaningAndUnknownArePreserved(){var report=OkeWorkloadHealth.summarizeNodes(json.readTree(node),List.of("worker-1"),1,0,0);var n=report.items().getFirst();assertEquals("False",n.ready());assertEquals("True",n.diskPressure());assertEquals("False",n.memoryPressure());assertEquals("Unknown",n.pidPressure());assertEquals("Unknown",n.networkUnavailable());assertTrue(n.unschedulable());}
 @Test void reportedPlatformAndLabelsAreKeptSeparate(){
  var value=json.readTree(node);((tools.jackson.databind.node.ObjectNode)value.path("metadata")).putObject("labels").put("kubernetes.io/arch","amd64").put("kubernetes.io/os","linux");
  var info=((tools.jackson.databind.node.ObjectNode)value.path("status")).putObject("nodeInfo");info.put("architecture","arm64").put("operatingSystem","linux");
  var n=OkeWorkloadHealth.summarizeNodes(value,List.of("worker-1"),1,0,0).items().getFirst();assertEquals("arm64",n.architecture());assertEquals("amd64",n.architectureLabel());assertEquals("불일치",n.platformState());
  info.put("architecture","amd64");assertEquals("일치",OkeWorkloadHealth.summarizeNodes(value,List.of("worker-1"),1,0,0).items().getFirst().platformState());
  info.remove("architecture");assertEquals("미확인",OkeWorkloadHealth.summarizeNodes(value,List.of("worker-1"),1,0,0).items().getFirst().platformState());
 }
 @Test void absentOrInvalidPlatformIsNotInferredFromLabel(){
  var value=json.readTree(node);((tools.jackson.databind.node.ObjectNode)value.path("metadata")).putObject("labels").put("kubernetes.io/arch","amd64");
  var n=OkeWorkloadHealth.summarizeNodes(value,List.of("worker-1"),1,0,0).items().getFirst();assertEquals("Unknown",n.architecture());assertEquals("미확인",n.platformState());
  ((tools.jackson.databind.node.ObjectNode)value.path("status")).putObject("nodeInfo").put("architecture","<script>");assertEquals("Unknown",OkeWorkloadHealth.summarizeNodes(value,List.of("worker-1"),1,0,0).items().getFirst().architecture());
 }
 @Test void unexpectedNodesAreNotDisclosed(){var result=OkeWorkloadHealth.summarizeNodes(json.readTree(node),List.of("other"),1,0,0);assertTrue(result.items().isEmpty());assertEquals("일부 조회됨",result.state());}
 @Test void disabledOptionNeverCallsNodeApi()throws Exception{var s=svc();var disabled=new OkeWorkloadHealth.Target("app","p","t","u","r","comp","cluster","/unused","ns","app",null,null);assertEquals("미설정",s.inspectNodes(disabled,List.of(pod("pod","worker-1")),0).state());verify(s,never()).readNodes(any(),anyList());}
 @Test void namesComeOnlyFromAssignedPodsAndAreDeduplicated()throws Exception{var s=svc();doReturn(json.readTree(node)).when(s).readNodes(enabled,List.of("worker-1"));var report=s.inspectNodes(enabled,List.of(pod("one","worker-1"),pod("two","worker-1"),pod("pending","")),0);assertEquals(1,report.total());verify(s).readNodes(enabled,List.of("worker-1"));}
 @Test void nodePagesBoundApiArgumentsAndClampPage()throws Exception{var s=svc();var pods=IntStream.range(0,21).mapToObj(i->pod("p"+i,String.format("worker-%02d",i))).toList();doReturn(json.readTree("{\"kind\":\"List\",\"items\":[]}")).when(s).readNodes(any(),anyList());var first=s.inspectNodes(enabled,pods,0);assertEquals(0,first.page());assertEquals(1,first.last());verify(s).readNodes(enabled,pods.subList(0,20).stream().map(OkeWorkloadHealth.Pod::node).toList());var last=s.inspectNodes(enabled,pods,99);assertEquals(1,last.page());verify(s).readNodes(enabled,List.of("worker-20"));}
 @Test void badNodeNameNeverBecomesCommandArgument()throws Exception{var s=svc();assertEquals("조회 불가",s.inspectNodes(enabled,List.of(pod("p","--all")),0).state());verify(s,never()).readNodes(any(),anyList());}
 @Test void nodePermissionFailureIsUnknownNotNotReady()throws Exception{var s=svc();doThrow(new RuntimeException("sensitive error")).when(s).readNodes(any(),anyList());var result=s.inspectNodes(enabled,List.of(pod("p","worker-1")),0);assertEquals("조회 불가",result.state());assertTrue(result.items().isEmpty());assertFalse(result.toString().contains("sensitive"));}
}
