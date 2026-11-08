package io.github.devmrko.companion.adapter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import io.github.devmrko.companion.domain.Models.Connection;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AgentHealthTest {
 @Test void checksOnlySelectedHealthAndDoesNotKeepStaleUp() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var fail=new java.util.concurrent.atomic.AtomicBoolean();
  server.createContext("/health",exchange->{assertEquals("Bearer synthetic-test",exchange.getRequestHeaders().getFirst("Authorization"));byte[] body="{\"status\":\"UP\",\"version\":\"0.6.0\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(fail.get()?503:200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try {var c=new Connection("p","t","u","r","n",false);var registry=mock(AgentConnectionRegistry.class);var registration=new AgentConnectionRegistry.Registration("id","app","oke","monitoring-http-v1","http://127.0.0.1:"+server.getAddress().getPort(),"ref","p","t","u","r","comp");when(registry.get(c,"r","comp","id")).thenReturn(registration);when(registry.token(c,"r","comp",registration)).thenReturn("synthetic-test");var health=new AgentHealth(registry);assertEquals("UP",health.check(c,"r","comp","id").state());fail.set(true);assertEquals("조회 실패",health.check(c,"r","comp","id").state());when(registry.get(c,"r","other","id")).thenThrow(new IllegalArgumentException());assertEquals("조회 실패",health.check(c,"r","other","id").state());}finally{server.stop(0);}
 }
}
