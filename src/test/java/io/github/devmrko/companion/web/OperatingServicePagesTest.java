package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class OperatingServicePagesTest {
 @Test void buildConfirmationIsBoundToScopeSessionAndConsumedOnce(){
  var catalog=mock(CatalogPages.class);var services=mock(OperatingServices.class);var env=mock(DeploymentEnvironments.class);
  var pages=new OperatingServicePages(catalog,mock(CatalogService.class),services,env,mock(DeploymentOperations.class),mock(OkeDeploymentInputs.class),mock(ArmBuilds.class),mock(NodePortAccess.class));
  var actor=new Connection("p","tenant","user","r","label",false);var auth=UsernamePasswordAuthenticationToken.authenticated(actor,"",List.of());
  when(catalog.selection(any(),any())).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","comp",30,""));when(services.list(any())).thenReturn(List.of());when(env.environments(any(),any(),any())).thenReturn(List.of());
  when(services.prepareBuild(any(),eq("app"),any())).thenReturn(new OperatingServices.BuildPlan(null,"a".repeat(40),null));
  when(services.build(any(),any())).thenReturn(new OperatingServices.Release("release",null,"","","","",""));
  var request=new MockHttpServletRequest();var model=new ExtendedModelMap();pages.buildPreview(auth,request,model,"app","a".repeat(40));String ticket=(String)model.get("ticket");
  assertThrows(RuntimeException.class,()->pages.build(auth,new MockHttpSession(),ticket,true));verify(services,never()).build(any(),any());
  assertEquals("redirect:/services?run=release",pages.build(auth,request.getSession(),ticket,true));assertThrows(RuntimeException.class,()->pages.build(auth,request.getSession(),ticket,true));verify(services,times(1)).build(any(),any());
  pages.buildPreview(auth,request,model,"app","a".repeat(40));String changed=(String)model.get("ticket");when(catalog.selection(any(),any())).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"other","comp",30,""));assertThrows(RuntimeException.class,()->pages.build(auth,request.getSession(),changed,true));verify(services,times(1)).build(any(),any());
 }
}
