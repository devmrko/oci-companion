package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class OkeWorkloadAccessPagesTest {
 @Test void executionIsOneUseAndScopeBoundWhileResultRemainsReadable(){
  var catalog=mock(CatalogPages.class);var environments=mock(DeploymentEnvironments.class);var access=mock(OkeWorkloadAccess.class);var pages=new OkeWorkloadAccessPages(catalog,environments,access);
  var c=new Connection("DEFAULT","tenancy","user","region","user",false);var auth=new UsernamePasswordAuthenticationToken(c,null,List.of());var request=new MockHttpServletRequest();var session=request.getSession();
  var scope=new CatalogPages.Selection(List.of(),List.of(),"region","comp",0,"selected");when(catalog.selection(c,session)).thenReturn(scope);when(environments.environments(any(),anyString(),anyString())).thenReturn(List.of());when(access.options(any(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(new OkeWorkloadAccess.Options("cluster","ENHANCED_CLUSTER","ns",List.of("sa"),List.of("bucket")));
  var m=new ExtendedModelMap();pages.page(auth,request,m,"env","PUBLIC_ENDPOINT","ns","");String form=(String)m.get("formTicket");var input=new OkeWorkloadAccess.Input("env","PUBLIC_ENDPOINT","ns","sa","bucket");var plan=new OkeWorkloadAccess.Plan(input,"cluster","policy","{}","job","owner");when(access.prepare(c,"region","comp",input)).thenReturn(plan);
  String redirect=pages.prepare(auth,session,form,input);String ticket=redirect.substring(redirect.indexOf("ticket=")+7);
  assertThatThrownBy(()->pages.prepare(auth,session,form,input)).isInstanceOf(RuntimeException.class);
  pages.execute(auth,session,ticket,new RedirectAttributesModelMap());assertThatThrownBy(()->pages.execute(auth,session,ticket,new RedirectAttributesModelMap())).isInstanceOf(RuntimeException.class);verify(access,times(1)).execute(c,"region","comp",plan);
  pages.page(auth,request,new ExtendedModelMap(),"","","",ticket);verify(access).result(c,"region","comp",plan);
  when(catalog.selection(c,session)).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"another","comp",0,"selected"));assertThatThrownBy(()->pages.page(auth,request,new ExtendedModelMap(),"","","",ticket)).isInstanceOf(RuntimeException.class);verify(access,times(1)).result(any(),anyString(),anyString(),any());
 }
}
