package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeConnectionSetupTest {
 @TempDir Path temp;
 final Connection c=new Connection("PROFILE","tenancy","user","region","name",false);
 final JsonMapper json=JsonMapper.builder().build();
 @Test void nativeExecIsDiscardedAndSelectedIdentityIsPinned(){
  String nativeConfig="clusters:\n- name: x\n  cluster:\n    server: https://127.0.0.1:6443\n    certificate-authority-data: Y2E=\nusers:\n- user:\n    exec:\n      command: untrusted-command\n";
  var doc=json.readTree(OciOkeWorkloadConnections.safeConfig(nativeConfig.getBytes(),c,"region","cluster","/config","/bin/oci"));
  var exec=doc.path("users").get(0).path("user").path("exec");assertEquals("/bin/oci",exec.path("command").asText());assertTrue(exec.path("args").toString().contains("PROFILE"));assertTrue(exec.path("args").toString().contains("/config"));assertFalse(doc.toString().contains("untrusted-command"));assertFalse(doc.toString().contains("insecure-skip-tls-verify"));
  assertThrows(IllegalArgumentException.class,()->OciOkeWorkloadConnections.safeConfig(nativeConfig.replace("https://","http://").getBytes(),c,"r","cluster","/config","oci"));
 }
 @Test void bindingCannotCrossProfileOrCompartment()throws Exception{
  var store=new OkeConnectionStore(temp.toString());var target=new OkeWorkloadHealth.Target("id","PROFILE","tenancy","user","region","comp","cluster","/kube","ns","app",null,null,true,"PRIVATE_ENDPOINT");
  store.save(c,"region","comp","id",target);assertEquals("PRIVATE_ENDPOINT",store.get(c,"region","comp","id").endpoint());assertNull(store.get(c,"region","other","id"));
  var other=new Connection("OTHER","tenancy","user","region","name",false);assertNull(store.get(other,"region","comp","id"));assertThrows(IllegalArgumentException.class,()->store.save(other,"region","comp","id",target));
 }
 @Test void deploymentMustStillExistBeforeRegistryWrite()throws Exception{
  var registry=mock(AgentConnectionRegistry.class);var reader=mock(OkeWorkloadHealth.class);var store=new OkeConnectionStore(temp.toString());
  var svc=spy(new OciOkeWorkloadConnections(mock(OkePreparation.class),reader,registry,store,temp.toString(),"/config","oci"));
  Path kube=Files.createTempFile(temp,"kube-",".json");var t=new OkeWorkloadHealth.Target("","PROFILE","tenancy","user","region","comp","cluster",kube.toString(),"ns","app",null,null);
  doReturn(t).when(svc).prepare(c,"region","comp","cluster","PUBLIC_ENDPOINT","ns","app");when(reader.read(t,"deployment","app")).thenReturn(json.readTree("{\"kind\":\"Deployment\",\"metadata\":{\"name\":\"other\",\"namespace\":\"ns\",\"uid\":\"uid\"}}"));
  assertThrows(IllegalArgumentException.class,()->svc.save(c,"region","comp",new OkeWorkloadConnections.Settings("","name","test","cluster","PUBLIC_ENDPOINT","ns","app",false)));verifyNoInteractions(registry);assertFalse(Files.exists(kube));
 }
 @Test void okeRegistrationNeedsNoHttpSecretAndHealthDoesNotClaimUp(){
  var registry=new AgentConnectionRegistry(temp.resolve("apps.json").toString(),temp.resolve("credentials.json").toString());
  String id=registry.registerOke(c,"region","comp","","name","test",new AgentConnectionRegistry.Dependency("OKE","cluster","ocid1.cluster.oc1.test"));
  assertEquals("HTTP health 미설정",new AgentHealth(registry).check(c,"region","comp",id).state());assertThrows(IllegalArgumentException.class,()->registry.get(c,"region","other",id));
 }
}
