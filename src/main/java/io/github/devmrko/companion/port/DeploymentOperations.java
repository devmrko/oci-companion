package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Readiness;
import java.time.Instant;
import java.util.List;

/** Application boundary shared by UI and future tool adapters. Context comes from authentication, never model output. */
public interface DeploymentOperations {
 record Context(Connection connection,String region,String compartment,String interaction) {}
 record Target(String pipeline,String sourcePipeline,String sourceRun) {}
 record Issue(String code,String message) {}
 record Assessment(Target target,Instant observedAt,DeploymentRuns.Plan plan,BuildHandoff.Proof proof,
                   List<DeploymentImages.Check> images,List<DeploymentImages.Check> architectures,
                   Readiness.Summary readiness,List<Issue> issues) {
  public boolean executable(){return plan!=null&&issues.isEmpty();}
 }
 record Prepared(String id,Instant expiresAt,Assessment assessment) {}
 record Started(String id,Target target) {}
 List<DeploymentRuns.Pipeline> pipelines(Context context);
 DeploymentRuns.History history(Context context,String pipeline,String page);
 DeploymentRuns.Run status(Context context,String pipeline,String run);
 BuildLogs.Page logs(Context context,String pipeline,String run,String page,String until);
 List<String> sourceImages(Context context,Target target);
 Assessment inspect(Context context,Target target);
 Prepared prepare(Context context,Target target);
 Started execute(Context context,String preparedId);
}
