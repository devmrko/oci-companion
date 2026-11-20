package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExportedBuildImageTest {
 String uri="icn.ocir.io/example/app@sha256:"+"a".repeat(64);
 BuildStageRunProgress stage(BuildPipelineStageRunProgress.Status state,ExportedVariable... values){return BuildStageRunProgress.builder().stageDisplayName("publish").buildPipelineStageId("stage").status(state).exportedVariables(ExportedVariableCollection.builder().items(List.of(values)).build()).build();}
 ExportedVariable variable(String name,String value){return ExportedVariable.builder().name(name).value(value).build();}
 @Test void onlySuccessfulStageAndNamedDigestOutputAreRead(){
  var value=variable("BUILD_IMAGE_REFERENCE",uri);
  assertEquals(uri,OciBuildHistory.exportedImage(stage(BuildPipelineStageRunProgress.Status.Succeeded,value)).orElseThrow().location());
  assertTrue(OciBuildHistory.exportedImage(stage(BuildPipelineStageRunProgress.Status.Failed,value)).isEmpty());
  assertTrue(OciBuildHistory.exportedImage(stage(BuildPipelineStageRunProgress.Status.InProgress,value)).isEmpty());
  assertTrue(OciBuildHistory.exportedImage(stage(BuildPipelineStageRunProgress.Status.Succeeded,variable("UNRELATED_TOKEN","private"))).isEmpty());
 }
 @Test void duplicateAndMutableOutputAreNotEvidence(){
  var value=variable("BUILD_IMAGE_REFERENCE",uri);
  assertTrue(OciBuildHistory.exportedImage(stage(BuildPipelineStageRunProgress.Status.Succeeded,value,value)).isEmpty());
  assertTrue(OciBuildHistory.exportedImage(stage(BuildPipelineStageRunProgress.Status.Succeeded,variable("BUILD_IMAGE_REFERENCE","icn.ocir.io/example/app:latest"))).isEmpty());
 }
}
