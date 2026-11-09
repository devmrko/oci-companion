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
 @Test void externalImageRequiresDigestAndVaultIsOnlyForExternalRegistry(){
  String digest="docker.io/library/nginx@sha256:"+"a".repeat(64);
  assertTrue(OciContainerPreparation.externalImage(digest));
  for(String invalid:List.of("docker.io/library/nginx:latest","https://"+digest,"user:password@"+digest,"icn.ocir.io/ns/repo@sha256:"+"a".repeat(64),digest+"?token=value"))assertFalse(OciContainerPreparation.externalImage(invalid));
  var external=new ContainerPreparation.Input("hello","ad","shape",1,2,"subnet",false,"",Map.of(),List.of(),List.of(),0,"",digest,"ocid1.vaultsecret.oc1.test");
  OciContainerPreparation.validate(external);
  var details=OciContainerPreparation.details("comp",external,digest);
  var secret=(com.oracle.bmc.containerinstances.model.CreateVaultImagePullSecretDetails)details.getImagePullSecrets().getFirst();assertEquals("docker.io/library/nginx",secret.getRegistryEndpoint());assertEquals(external.pullSecret(),secret.getSecretId());
  var ambiguous=new ContainerPreparation.Input("hello","ad","shape",1,2,"subnet",false,"oci-image",Map.of(),List.of(),List.of(),0,"",digest,"");assertThrows(RuntimeException.class,()->OciContainerPreparation.validate(ambiguous));
  var sg=com.oracle.bmc.core.model.RouteRule.builder().networkEntityId("ocid1.servicegateway.test").destination("all-icn-services-in-oracle-services-network").build();assertFalse(OciContainerPreparation.externalRegistryRoute(List.of(sg)));
 }
 @Test void vaultMetadataIsScopedAndRotationInvalidatesPreview() throws Exception {
  var adapter=spy(new OciContainerPreparation(mock(OciBuildHistory.class)));
  var input=new ContainerPreparation.Input("hello","ad","shape",1,2,"subnet",false,"",Map.of(),List.of(),List.of(),0,"","docker.io/library/nginx@sha256:"+"a".repeat(64),"ocid1.vaultsecret.oc1.test");
  doReturn(new ContainerPreparation.Options(List.of("ad"),List.of(new ContainerPreparation.Shape("shape","cpu",new io.github.devmrko.companion.port.VmSizing.Limits(1,8,1,128,1,16))),List.of(new ContainerPreparation.Subnet("subnet","private","vcn",true)))).when(adapter).options(c,"r","comp","ad");
  var net=mock(com.oracle.bmc.core.VirtualNetworkClient.class);doReturn(net).when(adapter).network(c,"r");
  when(net.getSubnet(any())).thenReturn(com.oracle.bmc.core.responses.GetSubnetResponse.builder().subnet(com.oracle.bmc.core.model.Subnet.builder().compartmentId("comp").routeTableId("rt").build()).build());
  when(net.getRouteTable(any())).thenReturn(com.oracle.bmc.core.responses.GetRouteTableResponse.builder().routeTable(com.oracle.bmc.core.model.RouteTable.builder().routeRules(List.of(com.oracle.bmc.core.model.RouteRule.builder().networkEntityId("ocid1.natgateway.test").destination("0.0.0.0/0").build())).build()).build());
  var vault=mock(com.oracle.bmc.vault.VaultsClient.class);doReturn(vault).when(adapter).vault(c,"r");
  when(vault.getSecret(any())).thenReturn(secret("comp",1L),secret("comp",2L));
  var before=adapter.preview(c,"r","comp",input);var after=adapter.preview(c,"r","comp",input);assertNotEquals(before.fingerprint(),after.fingerprint());
  when(vault.getSecret(any())).thenReturn(secret("other-compartment",2L));assertThrows(RuntimeException.class,()->adapter.preview(c,"r","comp",input));
 }
 private static com.oracle.bmc.vault.responses.GetSecretResponse secret(String compartment,long version){return com.oracle.bmc.vault.responses.GetSecretResponse.builder().secret(com.oracle.bmc.vault.model.Secret.builder().compartmentId(compartment).currentVersionNumber(version).lifecycleState(com.oracle.bmc.vault.model.Secret.LifecycleState.Active).build()).build();}
}
