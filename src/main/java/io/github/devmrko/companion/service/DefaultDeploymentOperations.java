package io.github.devmrko.companion.service;

import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.error.BusinessException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** No servlet/session dependency. Cloud adapters retain native authorization and scope checks. */
@Service
public class DefaultDeploymentOperations implements DeploymentOperations {
 private final DeploymentRuns runs;private final BuildHandoff handoff;private final DeploymentLogs logs;
 private final DeploymentImages images;private final DeploymentArchitectures architectures;private final Clock clock;
 private record Pending(Context context,Prepared prepared) {}
 private final Map<String,Pending> pending=new LinkedHashMap<>();
 @org.springframework.beans.factory.annotation.Autowired
 public DefaultDeploymentOperations(DeploymentRuns runs,BuildHandoff handoff,DeploymentLogs logs,DeploymentImages images,DeploymentArchitectures architectures){this(runs,handoff,logs,images,architectures,Clock.systemUTC());}
 DefaultDeploymentOperations(DeploymentRuns runs,BuildHandoff handoff,DeploymentLogs logs,DeploymentImages images,DeploymentArchitectures architectures,Clock clock){this.runs=runs;this.handoff=handoff;this.logs=logs;this.images=images;this.architectures=architectures;this.clock=clock;}
 private void validate(Context c){if(c==null||c.connection()==null||c.region()==null||c.region().isBlank()||c.compartment()==null||c.compartment().isBlank()||c.interaction()==null||c.interaction().isBlank())throw Errors.SCOPE.exception();}
 private void target(Target t){if(t==null||t.pipeline()==null||t.pipeline().isBlank()||t.sourcePipeline()==null||t.sourceRun()==null)throw Errors.INVALID.exception();if(t.sourcePipeline().isBlank()!=t.sourceRun().isBlank())throw Errors.HANDOFF.exception();}
 public List<DeploymentRuns.Pipeline> pipelines(Context c){validate(c);return runs.pipelines(c.connection(),c.region(),c.compartment());}
 public DeploymentRuns.History history(Context c,String pipeline,String page){validate(c);return runs.history(c.connection(),c.region(),c.compartment(),pipeline,page);}
 public DeploymentRuns.Run status(Context c,String pipeline,String run){validate(c);return runs.run(c.connection(),c.region(),c.compartment(),pipeline,run);}
 public BuildLogs.Page logs(Context c,String pipeline,String run,String page,String until){validate(c);return logs.read(c.connection(),c.region(),c.compartment(),pipeline,run,page,until);}
 public List<String> sourceImages(Context c,Target t){validate(c);if(t.sourcePipeline().isBlank()!=t.sourceRun().isBlank())throw Errors.HANDOFF.exception();return t.sourceRun().isBlank()?List.of():handoff.images(c.connection(),c.region(),c.compartment(),t.sourcePipeline(),t.sourceRun());}
 public Assessment inspect(Context c,Target t){
  validate(c);target(t);var observed=clock.instant();DeploymentRuns.Plan plan=null;BuildHandoff.Proof proof=null;
  List<DeploymentImages.Check> imageChecks=List.of(),platformChecks=List.of();var issues=new ArrayList<Issue>();
  try{
   plan=runs.plan(c.connection(),c.region(),c.compartment(),t.pipeline());
   if(!t.sourceRun().isBlank())proof=handoff.verify(c.connection(),c.region(),c.compartment(),t.sourcePipeline(),t.sourceRun(),t.pipeline());
   imageChecks=List.copyOf(images.inspect(c.connection(),c.region(),c.compartment(),plan.images()));
   if(imageChecks.stream().anyMatch(x->x.state()==DeploymentImages.State.BLOCKED))throw Errors.DEPLOYMENT_IMAGE.exception();
   platformChecks=List.copyOf(architectures.inspect(c.connection(),c.region(),plan));
   if(platformChecks.stream().anyMatch(x->x.state()==DeploymentImages.State.BLOCKED))throw Errors.DEPLOYMENT_ARCHITECTURE.exception();
  }catch(BusinessException e){issues.add(new Issue(e.code(),e.getMessage()));}
  return new Assessment(t,observed,plan,proof,imageChecks,platformChecks,Readiness.deployment(plan,proof,!t.sourceRun().isBlank(),imageChecks,platformChecks),List.copyOf(issues));
 }
 public Prepared prepare(Context c,Target t){
  var assessment=inspect(c,t);var expires=clock.instant().plusSeconds(600);
  if(!assessment.executable())return new Prepared("",expires,assessment);
  var prepared=new Prepared(UUID.randomUUID().toString(),expires,assessment);
  synchronized(pending){
   pending.entrySet().removeIf(e->!e.getValue().prepared().expiresAt().isAfter(clock.instant()));
   var own=pending.entrySet().stream().filter(e->e.getValue().context().equals(c)).map(Map.Entry::getKey).toList();if(own.size()>=32)pending.remove(own.getFirst());
   if(pending.size()>=256)pending.remove(pending.keySet().iterator().next());
   pending.put(prepared.id(),new Pending(c,prepared));
  }
  return prepared;
 }
 public Started execute(Context c,String id){
  validate(c);Pending item;
  synchronized(pending){item=pending.get(id);if(item==null||!item.prepared().expiresAt().isAfter(clock.instant())){pending.remove(id);throw Errors.BUILD_CHANGED.exception();}if(!item.context().equals(c))throw Errors.SCOPE.exception();pending.remove(id);}
  var previous=item.prepared().assessment();var t=previous.target();
  // Re-read native state for every execution, including non-HTTP callers. Failed attempts consume the plan.
  var current=inspect(c,t);
  if(!current.executable()||!current.plan().fingerprint().equals(previous.plan().fingerprint())||!Objects.equals(current.proof()==null?null:current.proof().fingerprint(),previous.proof()==null?null:previous.proof().fingerprint()))throw Errors.BUILD_CHANGED.exception();
  return new Started(runs.start(c.connection(),c.region(),c.compartment(),t.pipeline(),previous.plan().fingerprint(),id),t);
 }
}
