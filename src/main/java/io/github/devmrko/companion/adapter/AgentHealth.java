package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component
public class AgentHealth {
 public record Status(String state,String version,String checkedAt) {}
 private final AgentConnectionRegistry registry;
 private final MonitoringTransport transport=new MonitoringTransport();
 public AgentHealth(AgentConnectionRegistry registry){this.registry=registry;}
 public Status check(Connection c,String r,String comp,String id){
  try {var x=registry.get(c,r,comp,id);if("oke-workload-v1".equals(x.adapter()))return new Status("HTTP health 미설정","—",Instant.now().toString());var n=transport.read(MonitoringTransport.checkedBase(x.baseUrl()).resolve("/health"),registry.token(c,r,comp,x));
   String state=n.path("status").asText("");String version=n.path("version").asText("미제공");
   return new Status(state.equals("UP")?"UP":"정상 응답 아님",version.length()>80?"형식 확인 필요":version,Instant.now().toString());
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return new Status("조회 실패", "—",Instant.now().toString());}
 }
}
