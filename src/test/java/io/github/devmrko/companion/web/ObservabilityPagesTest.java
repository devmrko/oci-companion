package io.github.devmrko.companion.web;
import io.github.devmrko.companion.adapter.AgentConnectionRegistry;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ObservabilitySetup;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ObservabilityPagesTest {
 @Test void productSwitchDoesNotExposeAnotherProductsEnvironmentAndFailedSaveKeepsInput(){
  var catalog=mock(CatalogPages.class);var setup=mock(ObservabilitySetup.class);var registry=mock(AgentConnectionRegistry.class);
  var pages=new ObservabilityPages(catalog,setup,registry);var c=new Connection("DEFAULT","t","u","r","name",false);
  var auth=new UsernamePasswordAuthenticationToken(c,null,List.of());var request=new MockHttpServletRequest();var session=request.getSession();
  when(catalog.selection(c,session)).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","comp",0,"selected"));
  var app=mock(AgentConnectionRegistry.Registration.class);when(app.id()).thenReturn("app");when(registry.get(c,"r","comp","app")).thenReturn(app);
  var saved=new ObservabilitySetup.Settings("signoz","https://signoz.example","https://collector.example","api","java");when(setup.get(c,"r","comp","app")).thenReturn(saved);
  when(setup.providers()).thenReturn(List.of(new ObservabilitySetup.Provider("pinpoint","Pinpoint","https://example.com","https://example.com","")));
  var model=new ExtendedModelMap();pages.page(auth,request,model,"app","pinpoint");
  assertThat(model.get("saved")).isNull();assertThat(model.get("instrumentation")).isNull();assertThat(model.get("configuredProvider")).isEqualTo("signoz");verify(setup,never()).instrumentation(any());
  var draft=new ObservabilitySetup.Settings("pinpoint","https://pinpoint.example","grpc://collector.example","api","java-pinpoint","bad name");
  doThrow(new IllegalArgumentException("invalid agent name")).when(setup).save(c,"r","comp","app",draft);
  var flash=new RedirectAttributesModelMap();String redirect=pages.save(auth,session,flash,"app","r","comp",draft.provider(),draft.dashboard(),draft.endpoint(),draft.service(),draft.runtime(),draft.agentId());
  assertThat(redirect).endsWith("&provider=pinpoint");assertThat(flash.getFlashAttributes().get("form")).isEqualTo(draft);
  model=new ExtendedModelMap();model.addAllAttributes(flash.getFlashAttributes());pages.page(auth,request,model,"app","pinpoint");assertThat(model.get("form")).isEqualTo(draft);
 }
}
