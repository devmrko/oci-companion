package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Errors;
import java.net.URI;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
@Component @Profile("!legacy")
public class JGitSourcePush implements SourcePush {
 private final JGitSpecifications git;private final GitCredentials auth;
 public JGitSourcePush(GitCredentials auth){this.git=new JGitSpecifications(auth);this.auth=auth;}
 private void validate(GitSpecifications.Target target,String region){
  JGitSpecifications.validate(target,region,false);
  if(!target.source().provider().equals("OCI")||!URI.create(target.source().url()).getHost().equals("devops.scmservice."+region+".oci.oraclecloud.com"))throw Errors.SCOPE.exception();
 }
 private UsernamePasswordCredentialsProvider credentials(Connection c,String region,GitSpecifications.Target t){return new UsernamePasswordCredentialsProvider(t.username(),auth.secret(c,region,URI.create(t.source().url()).getHost(),t.secret()));}
 static void absent(String url,String branch,CredentialsProvider credentials)throws Exception {
  if(Git.lsRemoteRepository().setRemote(url).setHeads(true).setCredentialsProvider(credentials).setTimeout(60).call().stream().anyMatch(x->x.getName().equals("refs/heads/"+branch)))throw Errors.GIT_CONFLICT.exception();
 }
 public Plan preview(Connection c,String region,GitSpecifications.Target source,GitSpecifications.Target destination){
  validate(destination,region);var credentials=credentials(c,region,destination);
  try{return git.withRepository(c,region,source,false,(repo,ignored,head)->{absent(destination.source().url(),destination.branch(),credentials);return new Plan(source,destination,head.name());});}finally{credentials.clear();}
 }
 static String pushNew(Git repo,String url,String branch,ObjectId head,CredentialsProvider credentials)throws Exception{
  absent(url,branch,credentials);
  var ref=repo.getRepository().updateRef("refs/heads/export");ref.setNewObjectId(head);ref.update();boolean success=false;
  for(var result:repo.push().setRemote(url).setRefSpecs(new RefSpec("refs/heads/export:refs/heads/"+branch)).setRefLeaseSpecs(new RefLeaseSpec("refs/heads/"+branch,ObjectId.zeroId().name())).setCredentialsProvider(credentials).setTimeout(120).call())for(var update:result.getRemoteUpdates()){
   if(update.getStatus()!=RemoteRefUpdate.Status.OK)throw Errors.GIT_CONFLICT.exception();success=true;
  }
  if(!success)throw Errors.GIT_ACCESS.exception();return head.name();
 }
 public String push(Connection c,String region,Plan plan){
  validate(plan.destination(),region);var credentials=credentials(c,region,plan.destination());
  try{return git.withRepository(c,region,plan.source(),true,(repo,ignored,head)->{if(!head.name().equals(plan.revision()))throw Errors.GIT_CONFLICT.exception();return pushNew(repo,plan.destination().source().url(),plan.destination().branch(),head,credentials);});}finally{credentials.clear();}
 }
}
