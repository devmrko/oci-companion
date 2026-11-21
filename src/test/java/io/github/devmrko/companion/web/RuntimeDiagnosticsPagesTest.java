package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.RuntimeDiagnostics;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RuntimeDiagnosticsPagesTest {
 @Test void dumpDownloadIsBoundToActorScopeAppAndExpiration(){var catalog=mock(CatalogPages.class);var diagnostics=mock(RuntimeDiagnostics.class);var pages=new RuntimeDiagnosticsPages(catalog,diagnostics);var c=new Connection("DEFAULT","t","u","r","name",false);var auth=new UsernamePasswordAuthenticationToken(c,null,List.of());var req=new MockHttpServletRequest();var session=req.getSession();when(catalog.selection(c,session)).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","comp",0,"selected"));when(diagnostics.threadDump(c,"r","comp","app")).thenReturn(new RuntimeDiagnostics.Dump("app",Instant.now(),"real-stack-format",1));
  pages.dump(auth,req,new ExtendedModelMap(),"app","r","comp");assertThat(pages.download(auth,session,"app").getBody()).isEqualTo("real-stack-format");var view=new ExtendedModelMap();pages.page(auth,req,view,"app",true);assertThat(view.get("dump")).isNotNull();assertThat(view.get("navSelection")).isNotNull();assertThatThrownBy(()->pages.download(auth,session,"other")).isInstanceOf(RuntimeException.class);
  session.setAttribute("runtimeDump",new RuntimeDiagnosticsPages.Stored(c,"r","comp","app",Instant.now().minusSeconds(1),new RuntimeDiagnostics.Dump("app",Instant.now(),"expired",1)));assertThatThrownBy(()->pages.download(auth,session,"app")).isInstanceOf(RuntimeException.class);
  when(catalog.selection(c,session)).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"changed","comp",0,"selected"));assertThatThrownBy(()->pages.dump(auth,req,new ExtendedModelMap(),"app","r","comp")).isInstanceOf(RuntimeException.class);verify(diagnostics,times(1)).threadDump(c,"r","comp","app");
 }
}
