package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkePodLogs;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkePodLogsTest {
 final Connection c=new Connection("p","t","u","r","name",false);
 final OkeWorkloadHealth.Target t=new OkeWorkloadHealth.Target("id","p","t","u","r","comp","cluster","/kube","ns","app",null,null);
 final JsonMapper json=JsonMapper.builder().build();
 final String pod="{\"kind\":\"Pod\",\"metadata\":{\"name\":\"pod\",\"namespace\":\"ns\",\"uid\":\"uid\"},\"spec\":{\"containers\":[{\"name\":\"main\"}],\"initContainers\":[{\"name\":\"init\"}]}}";
 KubectlPodLogs service(OkeWorkloadHealth reader)throws Exception{when(reader.target(c,"r","comp","id")).thenReturn(t);var svc=spy(new KubectlPodLogs(reader));doReturn(new OkePodLogs.Choices("ns","app",List.of(new OkePodLogs.Pod("pod","uid",List.of("main","init"))))).when(svc).choices(t);when(reader.read(t,"pod","pod")).thenReturn(json.readTree(pod));return svc;}
 @Test void limitsAndPreviousUseFixedArguments()throws Exception{var reader=mock(OkeWorkloadHealth.class);var svc=service(reader);when(reader.command(eq(t),anyList(),eq(65536))).thenReturn("hello\n".getBytes());assertEquals("hello\n",svc.read(c,"r","comp","id","pod","uid","main",true,200).text());verify(reader).command(t,List.of("logs","pod","--container=main","--tail=200","--limit-bytes=65536","--timestamps=true","--pod-running-timeout=3s","--previous=true"),65536);}
 @Test void staleUidAndForeignContainerNeverReadLogs()throws Exception{var reader=mock(OkeWorkloadHealth.class);var svc=service(reader);assertEquals("조회 불가",svc.read(c,"r","comp","id","pod","old","main",false,200).state());assertEquals("조회 불가",svc.read(c,"r","comp","id","pod","uid","foreign",false,200).state());verify(reader,never()).command(any(),anyList(),anyInt());}
 @Test void replacementDuringLogReadDiscardsOutput()throws Exception{var reader=mock(OkeWorkloadHealth.class);var svc=service(reader);when(reader.read(t,"pod","pod")).thenReturn(json.readTree(pod),json.readTree(pod.replace("\"uid\":\"uid\"","\"uid\":\"replacement\"")));when(reader.command(eq(t),anyList(),eq(65536))).thenReturn("must-not-display".getBytes());var result=svc.read(c,"r","comp","id","pod","uid","main",false,100);assertEquals("조회 불가",result.state());assertEquals("",result.text());}
 @Test void deniedAndEmptyLogsAreDistinct()throws Exception{var reader=mock(OkeWorkloadHealth.class);var svc=service(reader);when(reader.command(eq(t),anyList(),eq(65536))).thenReturn(new byte[0]);assertEquals("조회 완료",svc.read(c,"r","comp","id","pod","uid","init",false,500).state());when(reader.command(eq(t),anyList(),eq(65536))).thenThrow(new IllegalArgumentException());assertEquals("조회 불가",svc.read(c,"r","comp","id","pod","uid","init",false,500).state());}
}
