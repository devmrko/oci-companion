package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkePreparation;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeConnectionRemovalTest {
 @TempDir Path temp;
 final Connection c=new Connection("p","t","u","r","name",false);
 @Test void onlyScopedGeneratedFilesAreDeletedAndCloudIsUntouched()throws Exception{
  var registry=new AgentConnectionRegistry(temp.resolve("apps.json").toString(),temp.resolve("creds.json").toString());var store=new OkeConnectionStore(temp.toString());var cloud=mock(OkePreparation.class);var reader=mock(OkeWorkloadHealth.class);var svc=new OciOkeWorkloadConnections(cloud,reader,registry,store,temp.toString(),"/config","oci");
  String id=registry.registerOke(c,"r","comp","","name","test",new AgentConnectionRegistry.Dependency("OKE","cluster","ocid1.cluster.test"));Path dir=Files.createDirectories(temp.resolve("oke-kubeconfigs"));Path kube=Files.writeString(dir.resolve("oke-test.json"),"{}");store.save(c,"r","comp",id,new OkeWorkloadHealth.Target(id,"p","t","u","r","comp","ocid1.cluster.test",kube.toString(),"ns","app",null,null));
  assertThrows(IllegalArgumentException.class,()->svc.remove(c,"r","other",id));assertTrue(Files.exists(kube));svc.remove(c,"r","comp",id);assertFalse(Files.exists(kube));assertNull(store.get(c,"r","comp",id));assertTrue(registry.list(c,"r","comp").isEmpty());verifyNoInteractions(cloud,reader);
 }
 @Test void administratorPathsCannotBeDeleted()throws Exception{
  var registry=new AgentConnectionRegistry(temp.resolve("apps.json").toString(),temp.resolve("creds.json").toString());var store=new OkeConnectionStore(temp.toString());var svc=new OciOkeWorkloadConnections(mock(OkePreparation.class),mock(OkeWorkloadHealth.class),registry,store,temp.toString(),"/config","oci");String id=registry.registerOke(c,"r","comp","","name","test",new AgentConnectionRegistry.Dependency("OKE","cluster","ocid1.cluster.test"));Path kube=Files.writeString(temp.resolve("administrator.json"),"{}");store.save(c,"r","comp",id,new OkeWorkloadHealth.Target(id,"p","t","u","r","comp","ocid1.cluster.test",kube.toString(),"ns","app",null,null));assertThrows(IllegalArgumentException.class,()->svc.remove(c,"r","comp",id));assertTrue(Files.exists(kube));assertEquals(1,registry.list(c,"r","comp").size());
 }
}
