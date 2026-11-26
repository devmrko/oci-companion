package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class OkeApiAccessTest {
 @TempDir Path directory;
 final Connection c=new Connection("p","tenant","user","r","label",false);
 @Test void overridesPersistAndApplyToCommonReaderTarget()throws Exception {
  var access=new OkeApiAccess(new JsonBuildSpecDraftStore(directory.toString()));var settings=new OkeApiAccess.Settings("https://127.0.0.1:6443","10.0.0.2","","","");access.save(c,"r","comp","cluster",settings);
  var reopened=new OkeApiAccess(new JsonBuildSpecDraftStore(directory.toString()));assertEquals(settings,reopened.read(c,"r","comp","cluster"));assertEquals("",reopened.read(c,"r","other","cluster").server());
  var target=new OkeWorkloadHealth.Target("id","p","tenant","user","r","comp","cluster","config","ns","app",null,null);
  var actual=reopened.apply(target);assertEquals(settings.server(),actual.server());assertEquals(settings.tlsServerName(),actual.tlsServerName());assertEquals("config",actual.kubeconfig());
 }
 @Test void sshArgumentsBindLoopbackAndKeepHostVerification(){var s=new OkeApiAccess.Settings("https://10.0.0.2:6443","10.0.0.2","jump.example","opc","/keys/oke");var args=OkeApiAccess.sshCommand(s,41234);assertTrue(args.contains("127.0.0.1:41234:10.0.0.2:6443"));assertTrue(args.contains("StrictHostKeyChecking=yes"));assertEquals("opc@jump.example",args.getLast());}
 @Test void rejectsUnsafeEndpointsAndPartialSshSettings(){
  for(String address:new String[]{"http://localhost:6443","https://user:pass@host","https://host/path","https://host?token=secret"})assertThrows(RuntimeException.class,()->OkeApiAccess.validate(new OkeApiAccess.Settings(address,"","","","")));
  assertThrows(RuntimeException.class,()->OkeApiAccess.validate(new OkeApiAccess.Settings("https://host","","-oProxyCommand=x","user","/key")));
  assertThrows(RuntimeException.class,()->OkeApiAccess.validate(new OkeApiAccess.Settings("https://host","","jump","user","relative")));
 }
}
