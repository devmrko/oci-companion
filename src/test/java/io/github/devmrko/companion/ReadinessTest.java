package io.github.devmrko.companion;
import io.github.devmrko.companion.service.Readiness;
import io.github.devmrko.companion.port.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReadinessTest {
 @Test void deploymentHandoffRetainsSuccessfulRunAndDoesNotOfferFailedArtifacts(){
  var steps=List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD"),new BuildHistory.Step("save","SUCCEEDED","DELIVER"));
  var artifacts=List.of(new BuildHistory.StoredArtifact("app","Artifact Registry","app.jar","1","a"));
  var success=new BuildHistory.Detail("run/1","run","SUCCEEDED","",steps,artifacts);
  assertEquals("/deployments/runs?sourcePipeline=pipe%2F1&sourceRun=run%2F1",Readiness.result(success,"pipe/1").items().get(3).href());
  var failed=new BuildHistory.Detail("run/1","run","FAILED","",steps,artifacts);
  assertEquals("/deployments",Readiness.result(failed,"pipe/1").items().get(3).href());
 }
 @Test void containerOnlyBuildDoesNotEnterVmFileVerification(){
  var steps=List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD"),new BuildHistory.Step("save","SUCCEEDED","DELIVER"));
  var image=new BuildHistory.StoredArtifact("image","Container Registry","repo:tag","sha256:abc","image-id");
  var detail=new BuildHistory.Detail("run","run","SUCCEEDED","",steps,List.of(image));
  assertFalse(Readiness.fileHandoff(detail));
  assertEquals("/deployments",Readiness.deploymentLink(detail,"pipeline"));
  assertEquals("/deployments",Readiness.result(detail,"pipeline").items().get(3).href());
 }
 private BuildDefinition.Definition definition(boolean source,boolean deliver){
  var stages=new ArrayList<BuildDefinition.Stage>();stages.add(new BuildDefinition.Stage("b","build","빌드","ACTIVE",List.of(),"image","build.yaml",60,source?List.of(new BuildDefinition.Source("s","s","OCI","repo","url","main",true)):List.of(),List.of()));
  if(deliver)stages.add(new BuildDefinition.Stage("d","save","산출물 전달","ACTIVE",List.of("b"),"","",0,List.of(),List.of()));
  return new BuildDefinition.Definition("test","project","ACTIVE","","v",stages);
 }
 @Test void optionalStorageIsNotWarningAndMissingEvidenceNeverPasses(){
  var summary=Readiness.prepare(definition(true,false),null,Map.of(),"p");
  assertTrue(summary.items().stream().anyMatch(i->i.key().equals("storage")&&i.state()==Readiness.State.NA&&!i.needed()));
  assertTrue(summary.items().stream().anyMatch(i->i.key().equals("spec")&&i.state()==Readiness.State.UNKNOWN));
  assertEquals(0,summary.action());assertTrue(summary.unknown()>0);
 }
 @Test void missingSourceFailsButFailedCloudReadIsUnknown(){
  var summary=Readiness.prepare(definition(false,true),null,Map.of(),"p");
  assertTrue(summary.items().stream().anyMatch(i->i.key().equals("source")&&i.state()==Readiness.State.FAIL));
  assertTrue(summary.items().stream().anyMatch(i->i.key().equals("storage")&&i.needed()&&i.state()==Readiness.State.UNKNOWN));
 }
 @Test void outputMismatchFailsAndUnreadSpecificationKeepsStorageUnknown(){
  var settings=new BuildSetup.Settings("v","test","",List.of(),List.of(new BuildSetup.Artifact("a","file","GENERIC_FILE","path")),List.of(),List.of(),List.of(new BuildSetup.Delivery("d","v","save",List.of(new BuildSetup.Mapping("missing","a")))));
  var spec=new BuildDefinition.Specification("content","",List.of("other · BINARY · out"),List.of());
  var bad=Readiness.prepare(definition(true,true),settings,Map.of("b",spec),"p");assertTrue(bad.items().stream().anyMatch(i->i.key().equals("storage")&&i.state()==Readiness.State.FAIL));
  var unknown=Readiness.prepare(definition(true,true),settings,Map.of(),"p");assertFalse(unknown.items().stream().anyMatch(i->i.key().equals("storage")&&i.state()==Readiness.State.PASS));
 }
 @Test void deploymentImageFailureIsRedAndPermissionsStayUnknown(){
  var plan=new DeploymentRuns.Plan("p","p","e",List.of(),"v","OKE","cluster","ns",List.of("image"));
  var summary=Readiness.deployment(plan,null,false,List.of(new DeploymentImages.Check("image",DeploymentImages.State.BLOCKED,"missing")),List.of());
  assertTrue(summary.items().stream().anyMatch(i->i.key().equals("image")&&i.state()==Readiness.State.FAIL));
  assertTrue(summary.items().stream().anyMatch(i->i.key().equals("permissions")&&i.state()==Readiness.State.UNKNOWN));
  assertTrue(summary.items().stream().anyMatch(i->i.key().equals("artifact")&&i.state()==Readiness.State.NA&&!i.needed()));
 }
 @Test void successfulBuildDoesNotProveTestsStorageOrDeployment(){
  var result=Readiness.result(new BuildHistory.Detail("r","r","SUCCEEDED","",List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD"))),"p");
  assertEquals(Readiness.State.PASS,result.items().get(0).state());assertEquals(Readiness.State.UNKNOWN,result.items().get(1).state());assertEquals(Readiness.State.NA,result.items().get(2).state());assertEquals(Readiness.State.UNKNOWN,result.items().get(3).state());
 }
}
