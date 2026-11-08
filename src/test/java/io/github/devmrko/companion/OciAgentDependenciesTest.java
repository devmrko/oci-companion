package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciAgentDependenciesTest {
 final Connection c=new Connection("p","t","u","r","n",false);
 final AgentConnectionRegistry.Dependency ref=new AgentConnectionRegistry.Dependency("VM","node","ocid1.instance.oc1.r.test");
 @TempDir Path temp;
 @Test void preservesUnknownAndDoesNotDiscloseOtherCompartmentState()throws Exception {
  var service=spy(new OciAgentDependencies("unused-test-config"));
  doReturn(new OciAgentDependencies.Snapshot("comp","STOPPED")).when(service).fetch(c,"r",ref);
  assertEquals("STOPPED",service.check(c,"r","comp",ref).state());assertTrue(service.check(c,"r","comp",ref).verified());
  var hidden=service.check(c,"r","other",ref);assertEquals("범위 불일치",hidden.state());assertFalse(hidden.verified());assertFalse(hidden.toString().contains("STOPPED"));
  doThrow(new RuntimeException("sensitive OCI detail")).when(service).fetch(c,"r",ref);
  var unknown=service.check(c,"r","comp",ref);assertEquals("조회 불가",unknown.state());assertFalse(unknown.verified());assertFalse(unknown.toString().contains("sensitive"));
 }
 @Test void activeClusterIsNotNodeHealth()throws Exception {
  var service=spy(new OciAgentDependencies("unused-test-config"));var cluster=new AgentConnectionRegistry.Dependency("OKE","cluster","ocid1.cluster.oc1.r.test");
  doReturn(new OciAgentDependencies.Snapshot("comp","ACTIVE")).when(service).fetch(c,"r",cluster);
  var result=service.check(c,"r","comp",cluster);assertEquals("ACTIVE",result.state());assertTrue(result.guidance().contains("별도로"));
 }
 @Test void legacyRegistryAndScopedReferences()throws Exception {
  var file=temp.resolve("apps.json");Files.writeString(file,"""
  [{"id":"id","name":"app","environment":"dev","adapter":"monitoring-http-v1","baseUrl":"http://127.0.0.1:1234","credentialRef":"ref","profile":"p","tenancy":"t","user":"u","region":"r","compartment":"comp"}]
  """);var registry=new AgentConnectionRegistry(file.toString(),temp.resolve("creds.json").toString());
  assertTrue(registry.get(c,"r","comp","id").dependencies().isEmpty());
  assertThrows(IllegalArgumentException.class,()->registry.dependency(c,"r","other","id",ref,false));
  registry.dependency(c,"r","comp","id",ref,false);assertEquals(1,registry.get(c,"r","comp","id").dependencies().size());
  assertThrows(IllegalArgumentException.class,()->registry.dependency(c,"r","comp","id",ref,false));
  for(int i=1;i<8;i++)registry.dependency(c,"r","comp","id",new AgentConnectionRegistry.Dependency("VM","n","ocid1.instance.oc1.r.n"+i),false);
  assertThrows(IllegalArgumentException.class,()->registry.dependency(c,"r","comp","id",new AgentConnectionRegistry.Dependency("VM","n","ocid1.instance.oc1.r.n9"),false));
  registry.dependency(c,"r","comp","id",ref,true);assertEquals(7,registry.get(c,"r","comp","id").dependencies().size());
 }
 @Test void rejectsWrongResourceTypeAndMalformedId(){assertThrows(IllegalArgumentException.class,()->new AgentConnectionRegistry.Dependency("ADB","db",ref.ocid()));assertThrows(IllegalArgumentException.class,()->new AgentConnectionRegistry.Dependency("VM","vm","ocid1.instance.x\n"));}
}
