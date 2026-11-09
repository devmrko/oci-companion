package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ContainerPreparation;
import com.oracle.bmc.containerinstances.ContainerInstanceClient;
import com.oracle.bmc.containerinstances.model.ContainerInstance;
import com.oracle.bmc.containerinstances.responses.CreateContainerInstanceResponse;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciContainerPreparationTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 ContainerPreparation.Input input(Map<String,String> env){return new ContainerPreparation.Input("hello","ad","shape",1,2,"subnet",false,"image",env,List.of(),List.of());}
 @Test void portableJsonKeepsDigestAndDoesNotExposePublicIpByDefault() throws Exception {
  var details=OciContainerPreparation.details("comp",input(Map.of("PORT","8080")),"icn.ocir.io/ns/hello@sha256:"+"a".repeat(64));
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var json=mapper.readTree(com.oracle.bmc.model.internal.JsonConverter.objectToJsonBlob(details));assertEquals("comp",json.get("compartmentId").asText());assertFalse(json.get("vnics").get(0).get("isPublicIpAssigned").asBoolean());assertTrue(json.get("containers").get(0).get("imageUrl").asText().contains("@sha256:"));assertEquals("8080",json.get("containers").get(0).get("environmentVariables").get("PORT").asText());
 }
 @Test void invalidEnvironmentNameCannotReachCloud(){assertThrows(RuntimeException.class,()->OciContainerPreparation.validate(input(Map.of("BAD-NAME","value"))));}
 @Test void changedPreviewCannotCreateAndNativeCreateCarriesRetryToken() throws Exception {
  var adapter=spy(new OciContainerPreparation(mock(OciBuildHistory.class)));var input=input(Map.of());var expected=new ContainerPreparation.Preview(input,"url","{}","a");
  doReturn(new ContainerPreparation.Preview(input,"url","{}","b")).when(adapter).preview(c,"r","comp",input);assertThrows(RuntimeException.class,()->adapter.create(c,"r","comp",expected,"token"));verify(adapter,never()).client(any(),any());
  doReturn(expected).when(adapter).preview(c,"r","comp",input);var client=mock(ContainerInstanceClient.class);doReturn(client).when(adapter).client(c,"r");when(client.createContainerInstance(any())).thenReturn(CreateContainerInstanceResponse.builder().containerInstance(ContainerInstance.builder().id("created").build()).build());assertEquals("created",adapter.create(c,"r","comp",expected,"token"));verify(client).createContainerInstance(argThat(r->r.getOpcRetryToken().equals("token")&&r.getCreateContainerInstanceDetails().getVnics().getFirst().getSubnetId().equals("subnet")));
 }
 @Test void logsRejectContainersOutsideSelectedInstanceBeforeRequest() throws Exception {
  var adapter=spy(new OciContainerPreparation(mock(OciBuildHistory.class)));
  doReturn(new ContainerPreparation.Detail("instance","test","ACTIVE","",List.of(new ContainerPreparation.Process("own","test","ACTIVE","","","image")))).when(adapter).read(c,"r","comp","instance");
  assertThrows(RuntimeException.class,()->adapter.logs(c,"r","comp","instance","other"));verify(adapter,never()).client(any(),any());
 }
 @Test void internetGatewayAloneCannotPullWithoutPublicIp(){
  var igw=com.oracle.bmc.core.model.RouteRule.builder().networkEntityId("ocid1.internetgateway.test").destination("0.0.0.0/0").build();
  var nat=com.oracle.bmc.core.model.RouteRule.builder().networkEntityId("ocid1.natgateway.test").destination("0.0.0.0/0").build();
  var sg=com.oracle.bmc.core.model.RouteRule.builder().networkEntityId("ocid1.servicegateway.test").destination("all-icn-services-in-oracle-services-network").build();
  assertFalse(OciContainerPreparation.privateRegistryRoute(List.of(igw)));assertTrue(OciContainerPreparation.privateRegistryRoute(List.of(nat)));assertTrue(OciContainerPreparation.privateRegistryRoute(List.of(sg)));
 }
}
