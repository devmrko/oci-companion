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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Opt-in isolated Function: simulates losing the caller's success persistence, never deploys an app. */
@EnabledIfEnvironmentVariable(named="COMPANION_LIVE_RECOVERY_TEST",matches="true")
class BuildRecoveryLiveTest {
 @Test void testedRegistryReceiptRecoversLostResultAndUnlocksBuilder()throws Exception{
  var json=JsonMapper.builder().build();var root=Path.of(System.getenv("COMPANION_LIVE_RECOVERY_DIR"));var state=json.readTree(Files.readString(root.resolve("resources.json")));
  var config=ConfigFileReader.parseDefault("JOUNGMINKOKOR_API");var c=new Connection("JOUNGMINKOKOR_API",config.get("tenancy"),config.get("user"),"ap-chuncheon-1","verification",false);String region="ap-chuncheon-1",compartment=config.get("tenancy");
  String data=System.getenv("COMPANION_LIVE_RECOVERY_DATA");var store=new JsonBuildSpecDraftStore(data);var resources=mock(OkeRuntimeResources.class);String crane=Path.of(".tools/function-image/bin/crane").toAbsolutePath().toString();var builds=spy(new RemoteArmBuilds(System.getProperty("user.home")+"/.oci/config",store,resources,crane,data));
  var clients=new OciBuildHistory(System.getProperty("user.home")+"/.oci/config");String function=state.path("function").asText();
  try(var manager=com.oracle.bmc.functions.FunctionsManagementClient.builder().build(clients.authentication(c))){manager.setRegion(region);var f=manager.getFunction(com.oracle.bmc.functions.requests.GetFunctionRequest.builder().functionId(function).build()).getFunction();assertEquals("companion-verify-recovery-builder",f.getDisplayName());assertNotEquals(state.path("productionFunction").asText(),function);assertEquals("ACTIVE",f.getLifecycleState().getValue());}
  var original=builds.builders(c,region,compartment).stream().filter(b->b.kind().equals("FUNCTION")&&b.functionId().equals(state.path("productionFunction").asText())).findFirst().orElseThrow();
  var builder=new ArmBuilds.Builder("verify-recovery","","","",original.secretEnvironment(),original.secretNamespace(),original.secretName(),original.secretEndpoint(),"FUNCTION",function);builds.saveBuilder(c,region,compartment,builder);
  String id=UUID.randomUUID().toString(),commit=Files.readString(root.resolve("commit.txt")).trim(),image=state.path("outputRepository").asText()+":verify-recovery-"+id;
  var engine=new FunctionImageBuilds(clients,crane);var target=engine.inspect(c,region,compartment,function,image);var input=new ArmBuilds.Input(builder.name(),"verification-git-archive",commit,state.path("outputRepo").asText(),image.substring(image.lastIndexOf(':')+1),"Dockerfile","pytest");var plan=new ArmBuilds.Plan(input,image,"verification source",builder.name(),builder,target.fingerprint());doReturn(plan).when(builds).preview(c,region,compartment,input);
  String scope=String.join("\n","arm-build-v1",c.profile(),c.tenancy(),c.user(),region,compartment),key=scope+"\n"+id;var run=new ArmBuilds.Run(id,"RUNNING","function",image,input.imageRepository(),"","",Instant.now().toString(),"");store.save(key,new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(run),Instant.now().toString()));store.save(key+"\nplan",new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(plan),Instant.now().toString()));
  var locks=new BuildExecutionLocks(data,store);
  try(var lease=locks.acquire(BuildExecutionLocks.physical(c,region,builder),key,false)){assertThrows(IllegalStateException.class,()->builds.start(c,region,compartment,plan,"",new char[0]));}
  assertThrows(IllegalStateException.class,()->builds.start(c,region,compartment,plan,"",new char[0]));assertEquals("UNKNOWN",builds.read(c,region,compartment,id).state());
  var process=new ProcessBuilder(System.getenv("COMPANION_LIVE_PYTHON"),System.getenv("COMPANION_LIVE_CREDENTIAL_HELPER")).start();byte[] raw=process.getInputStream().readAllBytes();assertEquals(0,process.waitFor());var auth=json.readTree(raw);Arrays.fill(raw,(byte)0);char[] password=auth.path("password").asText().toCharArray();
  try{
   long start=System.nanoTime();String digest=engine.build(c,region,compartment,plan,root.resolve("source.tar"),auth.path("username").asText(),password,id);
   assertThrows(Exception.class,()->engine.recover(plan,UUID.randomUUID().toString(),auth.path("username").asText(),password.clone()));
   when(resources.pullCredentials(any(),any(),any(),any(),any(),any(),any(),any())).thenAnswer(inv->new RegistryCredentials(auth.path("username").asText(),password));
   var recovered=builds.reconcile(c,region,compartment,id);assertEquals("SUCCEEDED",recovered.state());assertEquals(digest,recovered.digest());assertEquals("recovered",recovered.phase());assertTrue(builds.pending(c,region,compartment).isEmpty());
   try(var next=locks.acquire(BuildExecutionLocks.physical(c,region,builder),scope+"\n"+UUID.randomUUID(),false)){next.resolved();}
   var result=Map.of("run",id,"digest",digest,"elapsedSeconds",(System.nanoTime()-start)/1_000_000_000L,"duplicateBlocked",true,"restartBlocked",true,"wrongReceiptRejected",true,"recovered",true);Files.writeString(root.resolve("result.json"),json.writeValueAsString(result));System.out.println("LIVE RECOVERY: "+json.writeValueAsString(result));
  }finally{Arrays.fill(password,'\0');builds.close();}
 }
}
