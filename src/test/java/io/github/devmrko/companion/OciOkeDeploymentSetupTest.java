package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.*;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkeDeploymentSetup.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciOkeDeploymentSetupTest {
 @Test void nativeConfigurationUsesPinnedManifestAndRollbackPolicy()throws Exception{
  var c=new Connection("DEFAULT","tenant","user","home","label",false);var clients=mock(OciBuildHistory.class);var d=mock(DevopsClient.class);when(clients.client(c,"r")).thenReturn(d);var adapter=spy(new OciOkeDeploymentSetup(clients,mock(OciDeploymentSetup.class)));
  var i=new Input("env","repo","image","hello","description","app-test","hello","amd64",1,250,256,8080,"/hello","","","","",true);var p=new Preview(i,"project","env","url","kind: Deployment\n","fingerprint");doReturn(p).when(adapter).preview(c,"r","comp",i);
  when(d.createDeployPipeline(any())).thenReturn(CreateDeployPipelineResponse.builder().deployPipeline(DeployPipeline.builder().id("pipeline").build()).build());when(d.createDeployArtifact(any())).thenReturn(CreateDeployArtifactResponse.builder().deployArtifact(DeployArtifact.builder().id("manifest").build()).build());when(d.createDeployStage(any())).thenReturn(CreateDeployStageResponse.builder().deployStage(OkeDeployStage.builder().id("stage").build()).build());
  assertTrue(adapter.create(c,"r","comp",p,"token").complete());verify(d).createDeployArtifact(argThat(x->x.getCreateDeployArtifactDetails().getArgumentSubstitutionMode()==DeployArtifact.ArgumentSubstitutionMode.None&&new String(((InlineDeployArtifactSource)x.getCreateDeployArtifactDetails().getDeployArtifactSource()).getBase64EncodedContent(),StandardCharsets.UTF_8).equals(p.yaml())));verify(d).createDeployStage(argThat(x->x.getCreateDeployStageDetails() instanceof CreateOkeDeployStageDetails s&&s.getNamespace().equals("app-test")&&s.getOkeClusterDeployEnvironmentId().equals("env")&&s.getRollbackPolicy() instanceof AutomatedDeployStageRollbackPolicy));
  when(d.createDeployStage(any())).thenThrow(new RuntimeException("connection lost"));var partial=adapter.create(c,"r","comp",p,"another");assertFalse(partial.complete());assertEquals("pipeline",partial.pipeline());assertEquals("manifest",partial.specification());
  doReturn(new Preview(i,"project","env","url",p.yaml(),"changed")).when(adapter).preview(c,"r","comp",i);assertThrows(RuntimeException.class,()->adapter.create(c,"r","comp",p,"third"));verify(d,times(2)).createDeployPipeline(any());
 }
}
