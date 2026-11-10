package io.github.devmrko.companion.adapter;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeEventsTest {
 final JsonMapper json=JsonMapper.builder().build();
 final List<OkeWorkloadHealth.Pod> pods=List.of(new OkeWorkloadHealth.Pod("app","Pending","NotReady",0,"","","uid"));
 String event(String uid,String reason,String message){return "{\"metadata\":{\"namespace\":\"ns\"},\"involvedObject\":{\"kind\":\"Pod\",\"name\":\"app\",\"namespace\":\"ns\",\"uid\":\""+uid+"\"},\"type\":\"Warning\",\"reason\":\""+reason+"\",\"message\":\""+message+"\",\"lastTimestamp\":\"2026-11-10T01:00:00Z\"}";}
 @Test void onlyExactCurrentPodUidWarningsAreDisclosed(){var result=OkeWorkloadHealth.summarizeEvents("ns",pods,json.readTree("{\"items\":["+event("other","Failed","private-message")+","+event("uid","Failed","Failed to pull image private-image-secret")+"]}"));assertEquals(1,result.items().size());assertTrue(result.items().getFirst().guidance().contains("이미지 받기 실패"));assertFalse(result.toString().contains("private-image-secret"));}
 @Test void schedulingEvidenceDoesNotImplyArchitectureMismatch(){var r=OkeWorkloadHealth.summarizeEvents("ns",pods,json.readTree("{\"items\":["+event("uid","FailedScheduling","0/1 nodes: Insufficient cpu, Insufficient memory")+"]}"));assertTrue(r.items().getFirst().guidance().contains("CPU 부족"));assertTrue(r.items().getFirst().guidance().contains("메모리 부족"));assertFalse(r.items().getFirst().guidance().contains("아키텍처"));}
 @Test void eventsV1AndCoreV1SupportedAndResultsBounded(){String modern=event("uid","BackOff","Back-off restarting").replace("involvedObject","regarding").replace("message","note");var result=OkeWorkloadHealth.summarizeEvents("ns",pods,json.readTree("{\"items\":["+String.join(",",Collections.nCopies(21,modern))+"]}"));assertEquals(20,result.items().size());assertTrue(result.items().getFirst().guidance().contains("재시도"));}
 @Test void permissionFailureIsNotNoWarnings()throws Exception{var svc=spy(new OkeWorkloadHealth("/unused","/unused",mock(AgentConnectionRegistry.class),mock(OciAgentDependencies.class)));var t=new OkeWorkloadHealth.Target("id","p","t","u","r","c","cluster","/unused","ns","app",null,null);doThrow(new IllegalArgumentException()).when(svc).readEvents(t);assertEquals("조회 불가",svc.inspectEvents(t,pods).state());assertEquals("조회 완료",OkeWorkloadHealth.summarizeEvents("ns",pods,json.readTree("{\"items\":[]}")).state());}
}
