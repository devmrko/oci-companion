package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.functions.*;
import com.oracle.bmc.functions.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real OCI deletion is restricted to the explicitly named disposable fixture repository. */
@EnabledIfEnvironmentVariable(named="COMPANION_LIVE_CACHE_MAINTENANCE",matches="true")
class BuildCacheMaintenanceLiveTest {
 @Test void automaticPruningDeletesOnlyUnreferencedOwnedOldFixture()throws Exception {
  var json=JsonMapper.builder().build();var root=Path.of(System.getenv("COMPANION_LIVE_CACHE_DIR"));var state=json.readTree(Files.readString(root.resolve("resources.json")));var config=ConfigFileReader.parseDefault("JOUNGMINKOKOR_API");
  String region="ap-chuncheon-1",comp=config.get("tenancy"),data=System.getenv("COMPANION_LIVE_CACHE_DATA"),repository=state.path("repository").asText();assertTrue(repository.endsWith("/companion/verify-cache-retention"));
  var c=new Connection("JOUNGMINKOKOR_API",comp,config.get("user"),region,"verification",false);var clients=new OciBuildHistory(System.getProperty("user.home")+"/.oci/config");var store=new JsonBuildSpecDraftStore(data);var locks=new BuildExecutionLocks(data,store);var journal=new BuildCacheJournal(store,locks);String key=journal.key(comp,region,repository);
  var registration=new RemoteArmBuilds(System.getProperty("user.home")+"/.oci/config",store,mock(OkeRuntimeResources.class),"crane",data);
  var original=registration.builders(c,region,comp).stream().filter(b->b.functionId().equals(state.path("productionFunction").asText())).findFirst().orElseThrow();
  var builder=new ArmBuilds.Builder("verify-cache-retention","","","",original.secretEnvironment(),original.secretNamespace(),original.secretName(),original.secretEndpoint(),"FUNCTION",state.path("function").asText());assertNotEquals(state.path("productionFunction").asText(),builder.functionId());
  try(var f=FunctionsManagementClient.builder().build(clients.authentication(c))){f.setRegion(region);var fn=f.getFunction(GetFunctionRequest.builder().functionId(builder.functionId()).build()).getFunction();assertEquals("companion-verify-cache-retention",fn.getDisplayName());assertEquals("ACTIVE",fn.getLifecycleState().getValue());}
  registration.saveBuilder(c,region,comp,builder);registration.close();
  Instant baseline=Instant.now();var entries=new ArrayList<BuildCacheJournal.Entry>();int n=0;for(var fixture:state.path("fixtures")){n++;entries.add(new BuildCacheJournal.Entry(fixture.path("digest").asText(),fixture.path("tag").asText(),fixture.path("lock").asText(),"runtime",baseline.toString(),baseline.plusSeconds(n==7?-10:n).toString(),n!=7));}
  journal.save(key,new BuildCacheJournal.State(entries,false));var maintenance=new OciBuildCacheMaintenance(clients,journal,locks,Clock.fixed(baseline.plus(Duration.ofDays(1)),ZoneOffset.UTC));
  var plan=maintenance.preview(c,region,comp,builder);assertEquals(7,plan.items().size());assertTrue(plan.items().stream().noneMatch(ArmBuilds.CacheItem::deletable));
  journal.save(key,new BuildCacheJournal.State(entries,true));assertThrows(IllegalStateException.class,()->maintenance.apply(c,region,comp,builder,plan,true));journal.save(key,new BuildCacheJournal.State(entries,false));
  maintenance.apply(c,region,comp,builder,maintenance.preview(c,region,comp,builder),true);assertTrue(journal.read(key).automatic());
  var future=new OciBuildCacheMaintenance(clients,journal,locks,Clock.fixed(baseline.plus(Duration.ofDays(90)),ZoneOffset.UTC));var aged=future.preview(c,region,comp,builder);String removable=entries.get(2).digest();assertEquals(Set.of(removable),aged.items().stream().filter(ArmBuilds.CacheItem::deletable).map(ArmBuilds.CacheItem::digest).collect(java.util.stream.Collectors.toSet()));
  long start=System.nanoTime();future.afterSuccess(c,region,comp,builder);
  try(var a=new OciDeploymentSetup(clients).artifacts(c,region)){
   List<com.oracle.bmc.artifacts.model.ContainerImageSummary> remaining=List.of();
   for(int attempt=0;attempt<30;attempt++){remaining=a.listContainerImages(ListContainerImagesRequest.builder().compartmentId(comp).repositoryId(state.path("repositoryId").asText()).build()).getContainerImageCollection().getItems();if(remaining.stream().noneMatch(i->removable.equals(i.getDigest())&&"AVAILABLE".equals(i.getLifecycleState().getValue())))break;Thread.sleep(1000);}
   var live=remaining.stream().filter(i->"AVAILABLE".equals(i.getLifecycleState().getValue())).map(i->i.getDigest()).collect(java.util.stream.Collectors.toSet());assertEquals(6,live.size());assertFalse(live.contains(removable));for(int i:new int[]{0,1,3,4,5,6})assertTrue(live.contains(entries.get(i).digest()));
  }
  assertEquals(6,journal.read(key).entries().size());
  try(var f=FunctionsManagementClient.builder().build(clients.authentication(c))){f.setRegion(region);assertEquals(state.path("productionDigest").asText(),f.getFunction(GetFunctionRequest.builder().functionId(state.path("productionFunction").asText()).build()).getFunction().getImageDigest());}
  var result=Map.of("deleted",1,"preserved",6,"stalePreviewRejected",true,"currentFunctionProtected",true,"configuredBaseProtected",true,"recentThreeProtected",true,"unownedProtected",true,"clockAdvanceDays",90,"automaticElapsedSeconds",(System.nanoTime()-start)/1_000_000_000L);
  Files.writeString(root.resolve("result.json"),json.writeValueAsString(result));System.out.println("LIVE CACHE MAINTENANCE: "+json.writeValueAsString(result));
 }
}
