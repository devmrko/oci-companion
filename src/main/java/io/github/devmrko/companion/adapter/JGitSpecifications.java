package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.port.GitSpecifications;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Errors;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.dircache.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.transport.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** HTTPS Git protocol; no CLI, hooks, working tree or application-side build execution. */
@Component @Profile("!legacy")
public class JGitSpecifications implements GitSpecifications {
    private final io.github.devmrko.companion.port.GitCredentials authentication;
    public JGitSpecifications(io.github.devmrko.companion.port.GitCredentials authentication){this.authentication=authentication;}
    public static void validate(Target t,String region) {validate(t,region,false);}
    static void validate(Target t,String region,boolean configuredHost) {
        try {
            var uri=URI.create(t.source().url());
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getPort()!=-1 || uri.getQuery()!=null || uri.getFragment()!=null
                || (!configuredHost && !Set.of("github.com","gitlab.com","devops.scmservice."+region+".oci.oraclecloud.com").contains(uri.getHost()))
                || !uri.getRawPath().matches("/[A-Za-z0-9_./-]+") || uri.getPath().contains("..")
                || !Repository.isValidRefName("refs/heads/"+t.branch()) || t.branch().length()>200
                || !t.path().matches("[A-Za-z0-9_-][A-Za-z0-9_./-]{0,200}\\.ya?ml") || Arrays.asList(t.path().split("/",-1)).stream().anyMatch(p->p.isEmpty()||p.equals("..")||p.equals(".")||p.equalsIgnoreCase(".git"))
                || (!t.secret().matches("ocid1\\.vaultsecret\\.[A-Za-z0-9.]+") && !t.secret().matches("env:[A-Z][A-Z0-9_]{0,40}")) || t.username().isBlank() || t.username().length()>300 || t.username().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException();
        }catch(Exception e){throw Errors.INVALID.exception();}
    }
    @FunctionalInterface interface Work<T>{T run(Git git,CredentialsProvider credentials,ObjectId head) throws Exception;}
    private <T> T withRepository(Connection c,String region,Target t,Work<T> work) {
        validate(t,region,authentication.allowedHost(URI.create(t.source().url()).getHost()));Path directory=null;UsernamePasswordCredentialsProvider credentials=null;
        try {
            credentials=new UsernamePasswordCredentialsProvider(t.username(),authentication.secret(c,region,URI.create(t.source().url()).getHost(),t.secret()));
            directory=Files.createTempDirectory("oci-spec-");
            try(var git=Git.init().setBare(true).setDirectory(directory.toFile()).call()) {
                var config=git.getRepository().getConfig();config.setString("http",null,"followRedirects","false");config.setBoolean("http",null,"sslVerify",true);config.save();
                var fetched=git.fetch().setRemote(t.source().url()).setRefSpecs(new RefSpec("refs/heads/"+t.branch()+":refs/heads/source")).setDepth(1).setCredentialsProvider(credentials).setTimeout(60).call();
                var head=git.getRepository().resolve("refs/heads/source");if(head==null)throw Errors.MISSING.exception();
                return work.run(git,credentials,head);
            }
        }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}
        catch(Exception e){throw Errors.GIT_ACCESS.exception();}
        finally {
            if(credentials!=null)credentials.clear();
            if(directory!=null)try(var paths=Files.walk(directory)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}catch(Exception ignored){}
        }
    }
    public File read(Connection c,String region,Target t) {
        return withRepository(c,region,t,(git,credentials,head)->{
            try(var walk=new RevWalk(git.getRepository());var tree=TreeWalk.forPath(git.getRepository(),t.path(),walk.parseCommit(head).getTree())) {
                if(tree==null)return new File(head.name(),"");
                if(tree.getFileMode(0)!=FileMode.REGULAR_FILE && tree.getFileMode(0)!=FileMode.EXECUTABLE_FILE)throw Errors.INVALID.exception();
                return new File(head.name(),new String(git.getRepository().open(tree.getObjectId(0)).getBytes(131072),StandardCharsets.UTF_8));
            }
        });
    }
    public String save(Connection c,String region,Target t,String expected,String content,String message) {
        if(!ObjectId.isId(expected) || content.isBlank() || content.getBytes(StandardCharsets.UTF_8).length>131072 || message.isBlank() || message.length()>200 || message.chars().anyMatch(Character::isISOControl))throw Errors.INVALID.exception();
        return withRepository(c,region,t,(git,credentials,head)->{
            if(!head.name().equals(expected))throw Errors.GIT_CONFLICT.exception();
            var repo=git.getRepository();
            try(var walk=new RevWalk(repo);var inserter=repo.newObjectInserter();var reader=repo.newObjectReader()) {
                var parent=walk.parseCommit(head);var cache=DirCache.read(reader,parent.getTree());
                var editor=cache.editor();var blob=inserter.insert(Constants.OBJ_BLOB,content.getBytes(StandardCharsets.UTF_8));
                editor.add(new DirCacheEditor.PathEdit(t.path()){public void apply(DirCacheEntry entry){entry.setFileMode(FileMode.REGULAR_FILE);entry.setObjectId(blob);}});editor.finish();
                var tree=cache.writeTree(inserter);if(tree.equals(parent.getTree()))return head.name();
                var commit=new CommitBuilder();commit.setTreeId(tree);commit.setParentId(head);commit.setMessage(message);
                var identity=new PersonIdent("OCI Companion","oci-companion@localhost");commit.setAuthor(identity);commit.setCommitter(identity);
                var id=inserter.insert(commit);inserter.flush();var ref=repo.updateRef("refs/heads/proposal");ref.setNewObjectId(id);ref.update();
                var results=git.push().setRemote(t.source().url()).setRefSpecs(new RefSpec("refs/heads/proposal:refs/heads/"+t.branch()))
                    .setRefLeaseSpecs(new RefLeaseSpec("refs/heads/"+t.branch(),head.name())).setCredentialsProvider(credentials).setTimeout(60).call();
                boolean success=false;
                for(var result:results)for(var update:result.getRemoteUpdates()) {
                    if(update.getStatus()!=RemoteRefUpdate.Status.OK && update.getStatus()!=RemoteRefUpdate.Status.UP_TO_DATE)throw Errors.GIT_CONFLICT.exception();success=true;
                }
                if(!success)throw Errors.GIT_ACCESS.exception();return id.name();
            }
        });
    }
}
