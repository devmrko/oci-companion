package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.OkeDeploymentInputs;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeDeploymentEvidenceTest {
 @TempDir Path temp;
 @Test void recordedExpectationSurvivesRestartAndFailedDigestOverridesNativeSuccess(){
  var store=new JsonBuildSpecDraftStore(temp.toString());var resources=mock(OkeRuntimeResources.class);var inputs=new OkeDeploymentInputs(store);var c=new Connection("p","tenant","user","r","label",false);
  var expected=new DeploymentImageVerification.Expected("ns","app",2,Map.of("app","r.ocir.io/ns/app@sha256:"+"a".repeat(64)));
  var proof=new OkeDeploymentEvidence.Proof("pipeline","env","PRIVATE_ENDPOINT",List.of(expected));var first=new OkeDeploymentEvidence(store,resources,inputs);first.save(c,"r","comp","run",proof);
  var evidence=new DeploymentImageVerification.Image("ns/app","pod","app","sha256:"+"a".repeat(64),"sha256:"+"b".repeat(64),true);
  when(resources.verifyImages(c,"r","comp","env","PRIVATE_ENDPOINT",expected)).thenReturn(new DeploymentImageVerification.Result("FAILED","실패: 이미지 불일치",List.of(evidence)));
  var restarted=new OkeDeploymentEvidence(store,resources,inputs);var nativeRun=new DeploymentRuns.Run("run","release","SUCCEEDED","");var checked=restarted.verify(c,"r","comp","pipeline",nativeRun,true);
  assertEquals("FAILED",checked.state());assertEquals("SUCCEEDED",checked.cloudState());assertEquals(List.of(evidence),checked.images());
  assertEquals("UNVERIFIED",restarted.verify(new Connection("other","tenant","user","r","label",false),"r","comp","pipeline",nativeRun,true).state());
  assertEquals("UNVERIFIED",restarted.verify(c,"r","other","pipeline",nativeRun,true).state());
  assertEquals("UNVERIFIED",restarted.verify(c,"r","comp","other",nativeRun,true).state());
  when(resources.verifyImages(any(),any(),any(),any(),any(),any())).thenReturn(new DeploymentImageVerification.Result("SUCCEEDED","matched",List.of(evidence)));
  assertEquals("FAILED",restarted.verify(c,"r","comp","pipeline",nativeRun,true).state());
  restarted.save(c,"r","comp","good-run",proof);assertEquals("SUCCEEDED",restarted.verify(c,"r","comp","pipeline",new DeploymentRuns.Run("good-run","release","SUCCEEDED",""),true).state());
 }
 @Test void missingEvidenceOrFailedReadNeverReportsOkeSuccess(){var store=new JsonBuildSpecDraftStore(temp.toString());var resources=mock(OkeRuntimeResources.class);var svc=new OkeDeploymentEvidence(store,resources,new OkeDeploymentInputs(store));var c=new Connection("p","t","u","r","",false);var nativeRun=new DeploymentRuns.Run("r","run","SUCCEEDED","");assertEquals("UNVERIFIED",svc.verify(c,"r","comp","pipeline",nativeRun,true).state());assertEquals("SUCCEEDED",svc.verify(c,"r","comp","pipeline",nativeRun,false).state());}
}
