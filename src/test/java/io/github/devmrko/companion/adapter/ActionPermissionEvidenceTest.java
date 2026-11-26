package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ActionPermissionEvidenceTest {
 @Test void actualAuthorizationErrorDiffersFromAmbiguousMissingResource()throws Exception{
  var clients=org.mockito.Mockito.mock(OciPipelineIam.class);var checker=new OciActionPermissions(clients,org.mockito.Mockito.mock(OciOkePreparation.class));var actor=new io.github.devmrko.companion.domain.Models.Connection("profile","tenancy","user","region","name",false);
  org.mockito.Mockito.when(clients.identity(actor)).thenThrow(new com.oracle.bmc.model.BmcException(403,"NotAuthorized","denied","request"));assertEquals(io.github.devmrko.companion.service.Readiness.Permission.DENIED,checker.inspect(actor,"target","policies").permission());
  org.mockito.Mockito.doThrow(new com.oracle.bmc.model.BmcException(404,"NotAuthorizedOrNotFound","ambiguous","request")).when(clients).identity(actor);assertEquals(io.github.devmrko.companion.service.Readiness.Permission.UNKNOWN,checker.inspect(actor,"target","policies").permission());
 }

 boolean grant(String text){return OciActionPermissions.grants(text,"group-id","Admins",new Date(1),new Date(2),"policies",Set.of("target","root"));}
 @Test void onlyUnconditionalApplicableManagementGrants(){assertTrue(grant("Allow group Admins to manage all-resources in tenancy"));assertTrue(grant("Allow group id group-id to manage policies in compartment id target"));assertFalse(grant("Allow group Admins to read policies in tenancy"));assertFalse(grant("Allow group Admins to manage policies in tenancy where request.operation='CreatePolicy'"));assertFalse(grant("Allow group Admins to manage policies in compartment id other"));assertFalse(grant("Allow group Other/Admins to manage policies in tenancy"));}
 @Test void replacementGroupNameCannotProveOlderPolicy(){assertFalse(OciActionPermissions.grants("Allow group Admins to manage policies in tenancy","id","Admins",new Date(3),new Date(2),"policies",Set.of("root")));}
}
