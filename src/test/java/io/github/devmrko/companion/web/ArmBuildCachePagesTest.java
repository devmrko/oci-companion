package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.CatalogService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ArmBuildCachePagesTest {
 @Test void cacheApprovalIsActorScopedExplicitAndSingleUse(){
  var actor=new Connection("profile","tenancy","user","region","",false);var auth=new UsernamePasswordAuthenticationToken(actor,"",List.of());var catalog=mock(CatalogPages.class);var builds=mock(ArmBuilds.class);var pages=new ArmBuildPages(catalog,mock(CatalogService.class),mock(Registry.class),builds,mock(DeploymentEnvironments.class),mock(FunctionBuilds.class));var session=new MockHttpSession();
  when(catalog.selection(actor,session)).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"region","compartment",0,"scope"));var plan=new ArmBuilds.CachePlan("builder","registry/repo",List.of(),"fingerprint",false);var ticket=new ArmBuildPages.CacheTicket("token",actor,"region","compartment",plan,Instant.now().plusSeconds(600));
  session.setAttribute("armCacheTicket",ticket);assertThrows(RuntimeException.class,()->pages.cacheApply(auth,session,new RedirectAttributesModelMap(),"token",false,true));verifyNoInteractions(builds);
  session.setAttribute("armCacheTicket",new ArmBuildPages.CacheTicket("token",new Connection("other","tenancy","user","region","",false),"region","compartment",plan,Instant.now().plusSeconds(600)));assertThrows(RuntimeException.class,()->pages.cacheApply(auth,session,new RedirectAttributesModelMap(),"token",true,true));verifyNoInteractions(builds);
  session.setAttribute("armCacheTicket",ticket);pages.cacheApply(auth,session,new RedirectAttributesModelMap(),"token",true,true);verify(builds).cacheApply(actor,"region","compartment",plan,true);assertThrows(RuntimeException.class,()->pages.cacheApply(auth,session,new RedirectAttributesModelMap(),"token",true,true));verifyNoMoreInteractions(builds);
 }
}
