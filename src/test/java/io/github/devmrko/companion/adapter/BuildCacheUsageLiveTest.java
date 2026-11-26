package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ConfigFileReader;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="COMPANION_LIVE_CACHE_USAGE",matches="true")
class BuildCacheUsageLiveTest {
 @Test void realFunctionCreationAndSuccessRecordOwnedCachePair()throws Exception {
  var json=JsonMapper.builder().build();var root=Path.of(System.getenv("COMPANION_LIVE_CACHE_DIR"));var state=json.readTree(Files.readString(root.resolve("resources.json")));String repo=state.path("repository").asText();assertTrue(repo.endsWith("/companion/verify-cache-retention"));assertNotEquals(state.path("productionFunction").asText(),state.path("function").asText());
  var config=ConfigFileReader.parseDefault("JOUNGMINKOKOR_API");String region="ap-chuncheon-1",comp=config.get("tenancy"),data=System.getenv("COMPANION_LIVE_CACHE_DATA");var c=new Connection("JOUNGMINKOKOR_API",comp,config.get("user"),region,"verification",false);var store=new JsonBuildSpecDraftStore(data);var locks=new BuildExecutionLocks(data,store);var journal=new BuildCacheJournal(store,locks);String key=journal.key(comp,region,repo);var previous=journal.read(key);var observed=new ArrayList<BuildCacheJournal.Use>();
  var clients=new OciBuildHistory(System.getProperty("user.home")+"/.oci/config");var engine=new FunctionImageBuilds(clients,Path.of(".tools/function-image/bin/crane").toAbsolutePath().toString(),(actor,r,use)->{journal.record(journal.key(actor.tenancy(),r,use.repository()),use,Instant.now());observed.add(use);});
  String id=UUID.randomUUID().toString(),commit=Files.readString(Path.of(".tools/production-cache-enable/commit.txt")).trim(),image=repo+":verify-usage-"+id;var builder=new ArmBuilds.Builder("verify-cache-retention","","","","","","","PRIVATE_ENDPOINT","FUNCTION",state.path("function").asText());var target=engine.inspect(c,region,comp,builder.functionId(),image);assertEquals(repo,target.sourceRepository());
  var input=new ArmBuilds.Input(builder.name(),"verification-git-archive",commit,state.path("repositoryId").asText(),image.substring(image.lastIndexOf(':')+1),"Dockerfile","pytest");var plan=new ArmBuilds.Plan(input,image,"verification archive",builder.name(),builder,target.fingerprint());
  var process=new ProcessBuilder(System.getenv("COMPANION_LIVE_PYTHON"),System.getenv("COMPANION_LIVE_CREDENTIAL_HELPER")).start();byte[] raw=process.getInputStream().readAllBytes();assertEquals(0,process.waitFor());var auth=json.readTree(raw);Arrays.fill(raw,(byte)0);char[] password=auth.path("password").asText().toCharArray();
  try(var lease=locks.acquire(BuildExecutionLocks.physical(c,region,builder),"isolated-cache-usage-"+id,false)){
   long start=System.nanoTime();String digest=engine.build(c,region,comp,plan,Path.of(".tools/production-cache-enable/source.tar"),auth.path("username").asText(),password,id);
   assertEquals(1,observed.size());assertEquals(2,observed.getFirst().created().size());var after=journal.read(key);assertEquals(previous.entries().size()+2,after.entries().size());assertTrue(after.entries().stream().filter(e->observed.getFirst().created().contains(e.digest())).allMatch(BuildCacheJournal.Entry::owned));assertEquals(digest,engine.recover(plan,id,auth.path("username").asText(),password.clone()));lease.resolved();
   var result=Map.of("elapsedSeconds",(System.nanoTime()-start)/1_000_000_000L,"createdCacheImages",2,"usageRecorded",true,"receiptVerified",true,"digest",digest);Files.writeString(root.resolve("usage-result.json"),json.writeValueAsString(result));System.out.println("LIVE CACHE USAGE: "+json.writeValueAsString(result));
  }finally{Arrays.fill(password,'\0');}
 }
}
