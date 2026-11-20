package io.github.devmrko.companion;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.companion.port.BuildHistory;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BuildImageIdentityTest {
 String digest="sha256:"+"a".repeat(64),address="icn.ocir.io/namespace/repo/app";
 @Test void exportedImageDoesNotInventDeliverStageSuccess(){
  var output=BuildImageIdentity.exported("publish",address+"@"+digest,"stage").orElseThrow();
  var detail=new BuildHistory.Detail("run","run","SUCCEEDED","",List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD")),List.of(output));
  assertTrue(BuildImageIdentity.inspect(detail).getFirst().verified());
  assertEquals("기록 없음",detail.storageState());
  assertTrue(Readiness.deploymentLink(detail,"pipeline").contains("sourceRun=run"));
  assertTrue(BuildImageIdentity.exported("publish",address+":latest","stage").isEmpty());
  assertTrue(BuildImageIdentity.exported("publish","secret-value","stage").isEmpty());
 }
 @Test void pinsRecordedHashWithoutResolvingCurrentTag(){
  assertEquals(address+"@"+digest,BuildImageIdentity.reference(address+":latest",digest));
  assertEquals(address+"@"+digest,BuildImageIdentity.reference(address+"@"+digest,digest));
 }
 @Test void missingMalformedAndConflictingEvidenceCannotIdentifyImage(){
  for(String hash:Arrays.asList(null,"","sha256:abc","a".repeat(64)))assertEquals("",BuildImageIdentity.reference(address+":latest",hash));
  assertEquals("",BuildImageIdentity.reference(address+"@sha256:"+"b".repeat(64),digest));
  for(String uri:Arrays.asList(null,"","https://"+address,address+":",address+"\n"))assertEquals("",BuildImageIdentity.reference(uri,digest));
 }
 @Test void onlySuccessfulDeliveryOffersBuildBoundLink(){
  var image=new BuildHistory.StoredArtifact("image","Container Registry",address+":latest",digest,"id");
  var steps=List.of(new BuildHistory.Step("build","SUCCEEDED","BUILD"),new BuildHistory.Step("deliver","SUCCEEDED","DELIVER"));
  var detail=new BuildHistory.Detail("run","run","SUCCEEDED","",steps,List.of(image));
  assertTrue(BuildImageIdentity.inspect(detail).getFirst().verified());
  assertTrue(Readiness.deploymentLink(detail,"pipeline").contains("sourceRun=run"));
  assertFalse(BuildImageIdentity.inspect(new BuildHistory.Detail("run","run","FAILED","",steps,List.of(image))).getFirst().verified());
 }
}
