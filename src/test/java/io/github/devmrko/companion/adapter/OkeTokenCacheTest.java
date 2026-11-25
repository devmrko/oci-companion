package io.github.devmrko.companion.adapter;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeTokenCacheTest {
 @TempDir Path directory;
 OkeWorkloadHealth.Target target(String cluster)throws Exception{
  var path=directory.resolve("config.json");Files.writeString(path,"{\"current-context\":\"selected\",\"users\":[{\"name\":\"profile\",\"user\":{\"exec\":{\"command\":\"oci\"}}}]}");return new OkeWorkloadHealth.Target("id","p","t","u","r","comp",cluster,path.toString(),"ns","app",null,null);
 }
 @Test void reusesTokenForFiveReadsWithoutChangingOriginalConfig()throws Exception{
  var cache=spy(new OkeTokenCache("config","oci"));var t=target("cluster");
  doAnswer(inv->("{\"status\":{\"token\":\"test-token\",\"expirationTimestamp\":\""+Instant.now().plusSeconds(600)+"\"}}").getBytes(StandardCharsets.UTF_8)).when(cache).generate(t);
  for(int n=0;n<5;n++){var tmp=cache.temporaryConfig(t);try{assertTrue(Files.readString(tmp).contains("test-token"));assertFalse(Files.readString(tmp).contains("\"exec\""));assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(tmp)));}finally{Files.deleteIfExists(tmp);}}
  verify(cache,times(1)).generate(t);assertFalse(Files.readString(Path.of(t.kubeconfig())).contains("test-token"));
 }
 @Test void rejectsAlreadyExpiredToken()throws Exception{var cache=spy(new OkeTokenCache("config","oci"));var t=target("cluster");doReturn("{\"status\":{\"token\":\"expired\",\"expirationTimestamp\":\"2000-01-01T00:00:00Z\"}}".getBytes(StandardCharsets.UTF_8)).when(cache).generate(t);assertThrows(Exception.class,()->cache.temporaryConfig(t));}
}
