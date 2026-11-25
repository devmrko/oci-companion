package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.OkeDeploymentInputs;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Capture approved expected digests, never infer them from the current mutable pipeline after execution. */
@Component
public class OkeDeploymentEvidence {
 public record Proof(String pipeline,String environment,String endpoint,List<DeploymentImageVerification.Expected> targets){}
 private final BuildSpecDraftStore store;private final OkeRuntimeResources resources;private final OkeDeploymentInputs inputs;
 private final JsonMapper json=JsonMapper.builder().build();
 public OkeDeploymentEvidence(BuildSpecDraftStore store,OkeRuntimeResources resources,OkeDeploymentInputs inputs){this.store=store;this.resources=resources;this.inputs=inputs;}
 String key(Connection c,String region,String compartment,String run){return String.join("\n","oke-deployment-evidence-v1",c.profile(),c.tenancy(),c.user(),region,compartment,run);}
 Proof capture(Connection c,String region,String compartment,String pipeline,DevopsClient d){
  var stages=new ArrayList<DeployStageSummary>();for(var stage:d.getPaginators().listDeployStagesRecordIterator(ListDeployStagesRequest.builder().deployPipelineId(pipeline).build()))stages.add(stage);
  if(stages.size()!=1)return null;var stage=d.getDeployStage(GetDeployStageRequest.builder().deployStageId(stages.getFirst().getId()).build()).getDeployStage();
  if(!(stage instanceof OkeDeployStage oke))return null;
  var artifacts=new ArrayList<DeployArtifact>();for(String id:oke.getKubernetesManifestDeployArtifactIds())artifacts.add(d.getDeployArtifact(GetDeployArtifactRequest.builder().deployArtifactId(id).build()).getDeployArtifact());
  String endpoint=inputs.read(c,region,compartment,oke.getOkeClusterDeployEnvironmentId()).map(OkeDeploymentSetup.Input::endpoint).orElse("PRIVATE_ENDPOINT");
  return new Proof(pipeline,oke.getOkeClusterDeployEnvironmentId(),endpoint,OkeManifests.expectations(artifacts,compartment,oke.getProjectId(),oke.getNamespace()));
 }
 void save(Connection c,String region,String compartment,String run,Proof proof){if(proof!=null)store.save(key(c,region,compartment,run),new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(proof),Instant.now().toString()));}
 DeploymentRuns.Run verify(Connection c,String region,String compartment,String pipeline,DeploymentRuns.Run run,boolean oke){
  if(!run.state().equals("SUCCEEDED"))return run;
  var proof=store.read(key(c,region,compartment,run.id())).map(x->json.readValue(x.content(),Proof.class)).orElse(null);
  if(!oke&&proof==null)return run;
  if(proof==null||!pipeline.equals(proof.pipeline())||proof.targets().isEmpty())return new DeploymentRuns.Run(run.id(),run.name(),"UNVERIFIED","OCI SUCCEEDED · 실행 당시 기대 이미지 기록 없음 · 성공 미확인",run.steps(),run.state(),List.of());
  var recorded=store.read(key(c,region,compartment,run.id())+"\nresult");
  if(recorded.isPresent())return json.readValue(recorded.get().content(),DeploymentRuns.Run.class);
  var evidence=new ArrayList<DeploymentImageVerification.Image>();String state="SUCCEEDED",detail="실제 Pod imageID 일치 · Ready 확인";
  for(var target:proof.targets()){
   DeploymentImageVerification.Result result;
   try{result=resources.verifyImages(c,region,compartment,proof.environment(),proof.endpoint(),target);}catch(Exception e){result=DeploymentImageVerification.Result.unknown();}
   if(result==null)result=DeploymentImageVerification.Result.unknown();evidence.addAll(result.images());
   if(!result.state().equals("SUCCEEDED")){if(state.equals("SUCCEEDED")||result.state().equals("FAILED")){state=result.state();detail=result.detail();}}
  }
  var result=new DeploymentRuns.Run(run.id(),run.name(),state,detail,run.steps(),run.state(),List.copyOf(evidence));
  if(!state.equals("UNVERIFIED"))store.save(key(c,region,compartment,run.id())+"\nresult",new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(result),Instant.now().toString()));
  return result;
 }
}
