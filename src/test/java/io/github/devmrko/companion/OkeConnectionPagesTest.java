package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkeWorkloadConnections;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeConnectionPagesTest {
 @Test void staleScopeCannotBrowseOrSave(){
  var c=new Connection("p","t","u","r","name",false);var auth=new UsernamePasswordAuthenticationToken(c,"unused",List.of());var catalog=mock(CatalogPages.class);var port=mock(OkeWorkloadConnections.class);var session=new MockHttpSession();when(catalog.selection(c,session)).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","current",0,"selected"));var pages=new OkeConnectionPages(catalog,port,new io.github.devmrko.companion.web.OkeToolReadiness("/missing","missing"));
  assertFalse(pages.options(auth,session,"r","old","cluster","PUBLIC_ENDPOINT","").error().isEmpty());pages.save(auth,session,new RedirectAttributesModelMap(),"r","old","","name","test","cluster","PUBLIC_ENDPOINT","ns","app",false);verifyNoInteractions(port);
 }
 @Test void failedSaveKeepsUserInputs(){
  var c=new Connection("p","t","u","r","name",false);var auth=new UsernamePasswordAuthenticationToken(c,"unused",List.of());var catalog=mock(CatalogPages.class);var port=mock(OkeWorkloadConnections.class);var session=new MockHttpSession();when(catalog.selection(c,session)).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","comp",0,"selected"));when(port.save(any(),anyString(),anyString(),any())).thenThrow(new IllegalArgumentException("RBAC 확인"));var flash=new RedirectAttributesModelMap();new OkeConnectionPages(catalog,port,new io.github.devmrko.companion.web.OkeToolReadiness("/missing","missing")).save(auth,session,flash,"r","comp","","name","test","cluster","PRIVATE_ENDPOINT","ns","app",true);var saved=(OkeWorkloadConnections.Settings)flash.getFlashAttributes().get("settings");assertEquals("ns",saved.namespace());assertEquals("PRIVATE_ENDPOINT",saved.endpoint());assertTrue(saved.inspectNodes());
 }
}
