package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkeDeploymentSetup;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, isolated existing verification pipeline only; never targets a production workload. */
@EnabledIfEnvironmentVariable(named="COMPANION_LIVE_DIGEST_TEST",matches="true")
@SpringBootTest(properties={"companion.data-dir=.tools/verification/data","companion.kubectl=/opt/homebrew/bin/kubectl","logging.level.com.oracle.bmc=ERROR"})
class OkeDeploymentEvidenceLiveTest {
 @Autowired OciBuildHistory clients;@Autowired OkeDeploymentEvidence evidence;@Autowired OciDeploymentRuns runs;
 final JsonMapper json=JsonMapper.builder().build();
 @Test void staleArtifactFailsAndAwaitedImmutableArtifactPasses()throws Exception {
  var root=Path.of(System.getenv("COMPANION_LIVE_DIGEST_DIR"));var state=json.readTree(Files.readString(root.resolve("resources.json")));
  String profile=System.getenv("COMPANION_LIVE_PROFILE"),region=System.getenv("COMPANION_LIVE_REGION");
  var config=ConfigFileReader.parseDefault(profile);String compartment=config.get("tenancy");var c=new Connection(profile,compartment,config.get("user"),region,"isolated verification",false);
  String pipeline=state.path("pipeline").asText(),stageId=state.path("stage").asText();
  try(var d=clients.client(c,region)){
   var p=d.getDeployPipeline(GetDeployPipelineRequest.builder().deployPipelineId(pipeline).build()).getDeployPipeline();assertTrue(p.getDisplayName().startsWith("companion-verify-"));
   var originalStage=(OkeDeployStage)d.getDeployStage(GetDeployStageRequest.builder().deployStageId(stageId).build()).getDeployStage();assertEquals(pipeline,originalStage.getDeployPipelineId());
   var original=d.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(originalStage.getKubernetesManifestDeployArtifactIds().getFirst()).build()).getDeployArtifact();
   var document=(ObjectNode)json.readTree(((InlineDeployArtifactSource)original.getDeployArtifactSource()).getBase64EncodedContent());String app=document.path("metadata").path("name").asText();assertTrue(app.startsWith("companion-verify-"));
   String namespace=document.path("metadata").path("namespace").asText();assertEquals("personal-services",namespace);
   var specification=(ObjectNode)document.path("spec");specification.set("strategy",json.valueToTree(Map.of("type","Recreate")));
   var container=(ObjectNode)document.path("spec").path("template").path("spec").path("containers").get(0);String desired=container.path("image").asText();assertTrue(desired.endsWith(System.getenv("COMPANION_LIVE_EXPECTED_DIGEST")));
   var expected=evidence.capture(c,region,compartment,pipeline,d);
   // Fault injection: keep the requested expectation but deliberately execute the old artifact.
   String old=desired.substring(0,desired.indexOf('@')+1)+System.getenv("COMPANION_LIVE_OLD_DIGEST");assertNotEquals(desired,old);container.put("image",old);
   replace(d,pipeline,stageId,p.getProjectId(),originalStage.getOkeClusterDeployEnvironmentId(),namespace,app,json.writeValueAsString(document));
   String negative=d.createDeployment(CreateDeploymentRequest.builder().createDeploymentDetails(CreateDeployPipelineDeploymentDetails.builder().deployPipelineId(pipeline).displayName("companion-verify-deliberate-old-artifact").build()).build()).getDeployment().getId();evidence.save(c,region,compartment,negative,expected);
   completed(d,negative);var rejected=runs.run(c,region,compartment,pipeline,negative);assertEquals("FAILED",rejected.state());assertTrue(rejected.detail().contains("이미지 불일치"));assertEquals(2,rejected.images().size());System.out.println("LIVE: old artifact rejected with actual Pod imageID mismatch");
   container.put("image",desired);replace(d,pipeline,stageId,p.getProjectId(),originalStage.getOkeClusterDeployEnvironmentId(),namespace,app,json.writeValueAsString(document));
   var plan=runs.plan(c,region,compartment,pipeline);String positive=runs.start(c,region,compartment,pipeline,plan.fingerprint(),UUID.randomUUID().toString());completed(d,positive);var accepted=runs.run(c,region,compartment,pipeline,positive);
   assertEquals("SUCCEEDED",accepted.state());assertEquals(2,accepted.images().size());assertTrue(accepted.images().stream().allMatch(i->i.ready()&&i.expected().equals(i.actual())));System.out.println("LIVE: new immutable artifact verified, 2/2 Ready and Pod digests match");
   var result=new LinkedHashMap<String,Object>();result.put("negativeRun",negative);result.put("positiveRun",positive);result.put("negative",rejected);result.put("positive",accepted);result.put("finishedAt",Instant.now().toString());var path=root.resolve("live-results.json");Files.writeString(path,json.writeValueAsString(result));path.toFile().setReadable(false,false);path.toFile().setReadable(true,true);
  }
 }
 void replace(DevopsClient d,String pipeline,String stageId,String project,String env,String ns,String app,String yaml)throws Exception {
  var stage=d.getDeployStage(GetDeployStageRequest.builder().deployStageId(stageId).build());var input=new OkeDeploymentSetup.Input(env,"","",app,"",ns,app,"arm64",2,150,512,8845,"/readyz","","","","",false);
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((yaml+UUID.randomUUID()).getBytes(StandardCharsets.UTF_8)));
  OciOkeDeploymentSetup.replaceManifest(d,(OkeDeployStage)stage.getDeployStage(),stage.getEtag(),new OkeDeploymentSetup.Preview(input,project,"verification","",yaml,hash));
 }
 void completed(DevopsClient d,String run)throws Exception {
  long deadline=System.nanoTime()+240_000_000_000L;
  while(System.nanoTime()<deadline){var state=d.getDeployment(GetDeploymentRequest.builder().deploymentId(run).build()).getDeployment().getLifecycleState();if(state==Deployment.LifecycleState.Succeeded)return;if(state==Deployment.LifecycleState.Failed||state==Deployment.LifecycleState.Canceled)fail("Verification native execution ended "+state);Thread.sleep(2000);}
  fail("Verification native execution exceeded four minutes");
 }
}
