package io.github.devmrko.companion.web;
import io.github.devmrko.companion.adapter.*;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AgentConnectionPagesTest {
 @Test void podTableSplitsTwentyAndClampsPage() {
  var c=new Connection("p","t","u","r","name",false);var auth=new UsernamePasswordAuthenticationToken(c,"unused",List.of());
  var catalog=mock(CatalogPages.class);var registry=mock(AgentConnectionRegistry.class);var health=mock(AgentHealth.class);var dependencies=mock(OciAgentDependencies.class);var workloads=mock(OkeWorkloadHealth.class);var request=new MockHttpServletRequest();
  when(catalog.selection(c,request.getSession())).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","comp",0,"selected"));
  var app=new AgentConnectionRegistry.Registration("id","app","oke","monitoring-http-v1","http://127.0.0.1:1","ref","p","t","u","r","comp");when(registry.get(c,"r","comp","id")).thenReturn(app);when(registry.list(c,"r","comp")).thenReturn(List.of(app));
  var pods=IntStream.range(0,21).mapToObj(i->new OkeWorkloadHealth.Pod("pod-"+i,"Running","Ready",0,"")).toList();
  when(workloads.check(c,"r","comp","id")).thenReturn(new OkeWorkloadHealth.Status("준비 완료","ns","app",21,21,21,"now","",pods));
  var controller=new AgentConnectionPages(catalog,registry,health,dependencies,workloads);var first=new ExtendedModelMap();controller.page(auth,request,first,"id",0,false,0);assertEquals(20,((List<?>)first.get("pods")).size());
  var second=new ExtendedModelMap();controller.page(auth,request,second,"id",0,false,99);assertEquals(1,second.get("podPage"));assertEquals(List.of(pods.get(20)),second.get("pods"));
 }
}
