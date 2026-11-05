package io.github.devmrko.companion.service;

import io.github.devmrko.companion.domain.Models.*;
import io.github.devmrko.companion.port.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
@org.springframework.context.annotation.Profile("legacy")
public class WorkspaceService {
    private final WorkspaceRepository workspaces;
    private final RunRepository runs;
    private final PipelineGateway gateway;
    private final List<BuildRecipe> recipes;
    public WorkspaceService(WorkspaceRepository workspaces,RunRepository runs,PipelineGateway gateway,List<BuildRecipe> recipes) {
        this.workspaces=workspaces; this.runs=runs; this.gateway=gateway; this.recipes=recipes;
    }
    public List<Workspace> list(Connection c) { return workspaces.list(c.scope()); }
    public Workspace get(Connection c,String id) { return workspaces.find(c.scope(),id).orElseThrow(Errors.MISSING::exception); }
    public List<Run> runs(Connection c,String id) { get(c,id); return runs.list(c.scope(),id); }
    public Workspace save(Connection c,String id,String name,String url,String branch,String recipe,String compartment,String build,String deploy) {
        name=clean(name,100); url=clean(url,500); branch=clean(branch,200);
        compartment=clean(compartment,255); build=clean(build,255); deploy=clean(deploy,255);
        try {
            URI uri=URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw Errors.INVALID.exception();
        } catch (IllegalArgumentException e) { throw Errors.INVALID.exception(); }
        if (name.isBlank() || branch.isBlank() || recipes.stream().noneMatch(r -> r.id().equals(recipe))) throw Errors.INVALID.exception();
        if (!c.demo() && (!compartment.matches("ocid1\\.(compartment|tenancy)\\.[A-Za-z0-9._-]+") || (!build.isBlank() && !build.startsWith("ocid1.devopsbuildpipeline.")) || (!deploy.isBlank() && !deploy.startsWith("ocid1.devopsdeploypipeline.")))) throw Errors.INVALID.exception();
        Instant created=Instant.now();
        if (id!=null && !id.isBlank()) created=get(c,id).createdAt(); else id=UUID.randomUUID().toString();
        Workspace w=new Workspace(id,name,url,branch,recipe,compartment,build,deploy,created);
        workspaces.save(c.scope(),w); return w;
    }
    public List<Pipeline> pipelines(Connection c,String id) { return gateway.pipelines(c,get(c,id).compartmentId()); }
    public Run start(Connection c,String id,String kind,String token,String expectedPipeline) {
        if (!Set.of("BUILD","DEPLOY").contains(kind)) throw Errors.INVALID.exception();
        try { if (!UUID.fromString(token).toString().equals(token)) throw Errors.INVALID.exception(); }
        catch (IllegalArgumentException e) { throw Errors.INVALID.exception(); }
        Workspace w=get(c,id);
        String pipeline="BUILD".equals(kind)?w.buildPipelineId():w.deployPipelineId();
        if (pipeline.isBlank()) throw Errors.INVALID.exception();
        if (!pipeline.equals(expectedPipeline)) throw Errors.CONFLICT.exception();
        var existing=runs.find(c.scope(),token);
        if (existing.isPresent()) return same(existing.get(),id,kind,pipeline);
        gateway.validate(c,w,kind);
        Instant now=Instant.now();
        Run run=new Run(token,id,kind,pipeline,null,"SUBMITTING",now,now);
        if (!runs.reserve(c.scope(),run)) return same(runs.find(c.scope(),token).orElseThrow(Errors.CONFLICT::exception),id,kind,pipeline);
        RemoteRun remote;
        try { remote=gateway.start(c,kind,pipeline,token); }
        catch (RuntimeException e) { runs.update(c.scope(),token,null,"UNKNOWN"); throw Errors.CLOUD.exception(); }
        runs.update(c.scope(),token,remote.id(),remote.status());
        return runs.find(c.scope(),token).orElseThrow(Errors.MISSING::exception);
    }
    public void refresh(Connection c,String workspaceId,String runId) {
        get(c,workspaceId);
        Run run=runs.find(c.scope(),runId).filter(r -> r.workspaceId().equals(workspaceId)).orElseThrow(Errors.MISSING::exception);
        if (run.remoteId()==null) throw Errors.CONFLICT.exception();
        RemoteRun remote=gateway.status(c,run.kind(),run.remoteId());
        runs.update(c.scope(),run.id(),remote.id(),remote.status());
    }
    private Run same(Run run,String id,String kind,String pipeline) {
        if (!run.workspaceId().equals(id)||!run.kind().equals(kind)||!run.pipelineId().equals(pipeline)) throw Errors.CONFLICT.exception();
        return run;
    }
    private String clean(String value,int max) {
        if (value==null || value.length()>max || value.chars().anyMatch(Character::isISOControl)) throw Errors.INVALID.exception();
        return value.trim();
    }
}
