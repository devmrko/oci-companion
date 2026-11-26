package io.github.devmrko.companion.web;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.PipelineIam;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PipelineIamPagesTest {
    final Connection c=new Connection("p","t","u","r","label",false);
    final CatalogPages catalog=mock(CatalogPages.class);
    final PipelineIam iam=mock(PipelineIam.class);
    final PipelineIamPages pages=new PipelineIamPages(catalog,iam);
    final MockHttpServletRequest request=new MockHttpServletRequest();
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(c,null,List.of());
    final PipelineIam.Check check=new PipelineIam.Check(true,false,"group",true,"rule",List.of("statement"),List.of(),"");
    final io.github.devmrko.companion.port.ActionPermissions permissions=mock(io.github.devmrko.companion.port.ActionPermissions.class);
    @org.junit.jupiter.api.BeforeEach void permissions(){pages.permissions(permissions);when(permissions.inspect(any(),any(),any())).thenReturn(new io.github.devmrko.companion.port.ActionPermissions.Evidence(io.github.devmrko.companion.service.Readiness.Permission.CONFIRMED,"actual policy","guidance"));}
    @Test void refusesUnknownWritePermission(){var token=preview();when(permissions.inspect(any(),any(),any())).thenReturn(new io.github.devmrko.companion.port.ActionPermissions.Evidence(io.github.devmrko.companion.service.Readiness.Permission.UNKNOWN,"read is not write","guidance"));assertThrows(RuntimeException.class,()->pages.create(auth,request.getSession(),new RedirectAttributesModelMap(),token,true));verify(iam,never()).create(any(),any(),any(),any(),any(),any());}
    String preview(){when(catalog.selection(c,request.getSession())).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","comp",0,"selected"));when(iam.inspect(c,"r","comp","pipeline")).thenReturn(check);var model=new ExtendedModelMap();assertEquals("pipeline-iam",pages.page(auth,request,model,"pipeline"));return (String)model.get("ticket");}
    @Test void requiresConfirmationAndConsumesTicket(){var token=preview();assertThrows(RuntimeException.class,()->pages.create(auth,request.getSession(),new RedirectAttributesModelMap(),token,false));assertThrows(RuntimeException.class,()->pages.create(auth,request.getSession(),new RedirectAttributesModelMap(),token,true));verify(iam,never()).create(any(),any(),any(),any(),any(),any());}
    @Test void rejectsChangedScope(){var token=preview();when(catalog.selection(c,request.getSession())).thenReturn(new CatalogPages.Selection(List.of(),List.of(),"r","other",0,"selected"));assertThrows(RuntimeException.class,()->pages.create(auth,request.getSession(),new RedirectAttributesModelMap(),token,true));verify(iam,never()).create(any(),any(),any(),any(),any(),any());}
    @Test void createsOnlyReviewedProposalOnce(){var token=preview();pages.create(auth,request.getSession(),new RedirectAttributesModelMap(),token,true);verify(iam).create(c,"r","comp","pipeline",check,token);assertThrows(RuntimeException.class,()->pages.create(auth,request.getSession(),new RedirectAttributesModelMap(),token,true));}
}
