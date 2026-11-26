package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ConfigFileReader;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ArmBuilds;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: dedicated verification Function only, no production deployment. */
@EnabledIfEnvironmentVariable(named="COMPANION_LIVE_CACHE_TEST",matches="true")
class FunctionDependencyCacheLiveTest {
 @Test void changedLockBuildsThroughAutomaticRuntimeHandoff()throws Exception {
  var json=JsonMapper.builder().build();var root=Path.of(System.getenv("COMPANION_LIVE_CACHE_DIR"));
  var state=json.readTree(Files.readString(root.resolve("resources.json")));
  var config=ConfigFileReader.parseDefault("JOUNGMINKOKOR_API");String region="ap-chuncheon-1",compartment=config.get("tenancy");
  var connection=new Connection("JOUNGMINKOKOR_API",compartment,config.get("user"),region,"isolated cache verification",false);
  var clients=new OciBuildHistory(System.getProperty("user.home")+"/.oci/config");
  String function=state.path("function").asText();
  try(var management=com.oracle.bmc.functions.FunctionsManagementClient.builder().build(clients.authentication(connection))){
   management.setRegion(region);var f=management.getFunction(com.oracle.bmc.functions.requests.GetFunctionRequest.builder().functionId(function).build()).getFunction();
   assertEquals("companion-verify-lock-builder",f.getDisplayName());assertNotEquals(state.path("productionFunction").asText(),function);
  }
  var builds=new FunctionImageBuilds(clients,Path.of(".tools/function-image/bin/crane").toAbsolutePath().toString());
  String commit=Files.readString(root.resolve("commit.txt")).trim();assertTrue(commit.matches("[a-f0-9]{40}"));
  var process=new ProcessBuilder(System.getenv("COMPANION_LIVE_PYTHON"),System.getenv("COMPANION_LIVE_CREDENTIAL_HELPER")).start();
  byte[] raw=process.getInputStream().readAllBytes();assertEquals(0,process.waitFor());var credentials=json.readTree(raw);Arrays.fill(raw,(byte)0);
  char[] password=credentials.path("password").asText().toCharArray();
  try {
   for(int run=1;run<=2;run++){
    String tag="verify-lock-"+commit.substring(0,12)+"-"+run,image=state.path("outputRepository").asText()+":"+tag;
    var inspected=builds.inspect(connection,region,compartment,function,image);
    var builder=new ArmBuilds.Builder("isolated-cache","","","","","","","PRIVATE_ENDPOINT","FUNCTION",function);
    var input=new ArmBuilds.Input(builder.name(),"verification-git-archive",commit,state.path("outputRepo").asText(),tag,"Dockerfile","pytest");
    var plan=new ArmBuilds.Plan(input,image,"verification commit",builder.name(),builder,inspected.fingerprint());
    long start=System.nanoTime();String digest=builds.build(connection,region,compartment,plan,root.resolve("source.tar"),credentials.path("username").asText(),password);
    assertTrue(digest.matches("sha256:[a-f0-9]{64}"));
    var result=Map.of("run",run,"commit",commit,"digest",digest,"elapsedSeconds",(System.nanoTime()-start)/1_000_000_000L,"finishedAt",Instant.now().toString());
    Files.writeString(root.resolve("build-"+run+".json"),json.writeValueAsString(result));System.out.println("LIVE CACHE: "+json.writeValueAsString(result));
   }
  } finally {Arrays.fill(password,'\0');}
 }
}
