package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentRollback;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciDeploymentRollback implements DeploymentRollback {
 private final OciBuildHistory clients;private final io.github.devmrko.companion.port.DeploymentRuns runs;
 public OciDeploymentRollback(OciBuildHistory clients,io.github.devmrko.companion.port.DeploymentRuns runs){this.clients=clients;this.runs=runs;}
 public Plan plan(Connection c,String region,String comp,String pipeline,String run){
  var current=runs.plan(c,region,comp,pipeline);
  try(var d=clients.client(c,region)){
   var old=d.getDeployment(GetDeploymentRequest.builder().deploymentId(run).build()).getDeployment();
   if(!comp.equals(old.getCompartmentId())||!pipeline.equals(old.getDeployPipelineId()))throw Errors.SCOPE.exception();
   if(old.getLifecycleState()!=Deployment.LifecycleState.Succeeded||old.getDeploymentExecutionProgress()==null||old.getDeploymentExecutionProgress().getDeployStageExecutionProgress()==null)throw Errors.DEPLOYMENT_INPUT.exception();
   var stages=new ArrayList<DeployStageSummary>();d.getPaginators().listDeployStagesRecordIterator(ListDeployStagesRequest.builder().deployPipelineId(pipeline).build()).forEach(stages::add);if(stages.size()!=1)throw Errors.DEPLOYMENT_INPUT.exception();
   String id=stages.getFirst().getId();var stage=d.getDeployStage(GetDeployStageRequest.builder().deployStageId(id).build()).getDeployStage();String env;
   if(stage instanceof OkeDeployStage s)env=s.getOkeClusterDeployEnvironmentId();else if(stage instanceof ComputeInstanceGroupDeployStage s)env=s.getComputeInstanceGroupDeployEnvironmentId();else throw Errors.DEPLOYMENT_INPUT.exception();
   var progress=old.getDeploymentExecutionProgress().getDeployStageExecutionProgress().get(id);if(progress==null||progress.getStatus()!=DeployStageExecutionProgress.Status.Succeeded)throw Errors.DEPLOYMENT_INPUT.exception();
   if(old.getDeployPipelineEnvironments()==null||old.getDeployPipelineEnvironments().getItems()==null||old.getDeployPipelineEnvironments().getItems().stream().noneMatch(e->env.equals(e.getDeployEnvironmentId())))throw Errors.SCOPE.exception();
   String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((current.fingerprint()+old.toString()+stage.toString()).getBytes(StandardCharsets.UTF_8)));
   return new Plan(pipeline,run,old.getDisplayName(),id,current.environment(),current.targetType(),hash);
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
 public String execute(Connection c,String r,String comp,Plan expected,String token){
  if(!expected.equals(plan(c,r,comp,expected.pipeline(),expected.run())))throw Errors.BUILD_CHANGED.exception();
  try(var d=clients.client(c,r)){return d.createDeployment(CreateDeploymentRequest.builder().opcRetryToken(token).createDeploymentDetails(CreateSingleDeployStageRedeploymentDetails.builder().deployPipelineId(expected.pipeline()).previousDeploymentId(expected.run()).deployStageId(expected.stage()).displayName("companion-restore-"+token).build()).build()).getDeployment().getId();}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
 }
}
