package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkePodLogs;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeLogPagesTest {
 @Test void staleScopeDoesNotReadLogs(){var c=new Connection("p","t","u","r","name",false);var auth=new UsernamePasswordAuthenticationToken(c,"unused",List.of());var catalog=mock(CatalogPages.class);var logs=mock(OkePodLogs.class);var request=new MockHttpServletRequest();when(catalog.selection(c,request.getSession())).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","current",0,"selected"));var model=new ExtendedModelMap();new OkeLogPages(catalog,logs).page(auth,request,model,"id","pod","uid","app",false,200,"r","old");assertTrue(model.containsAttribute("logError"));verifyNoInteractions(logs);}
}
