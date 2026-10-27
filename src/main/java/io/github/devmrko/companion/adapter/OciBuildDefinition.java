package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildDefinition;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class OciBuildDefinition implements BuildDefinition {
    private final OciBuildHistory clients;
    private final io.github.devmrko.companion.port.SourceVersions versions;
    public OciBuildDefinition(OciBuildHistory clients) { this(clients,new OciSourceVersions(clients)); }
    @org.springframework.beans.factory.annotation.Autowired public OciBuildDefinition(OciBuildHistory clients,io.github.devmrko.companion.port.SourceVersions versions) { this.clients=clients;this.versions=versions; }
    private io.github.devmrko.companion.domain.Catalog.Source registered(Source s){return new io.github.devmrko.companion.domain.Catalog.Source("",s.repositoryId().isEmpty()?"EXTERNAL":"OCI",s.name(),s.url(),s.branch(),s.repositoryId());}
    public boolean validCommit(Connection c,String region,Source source,String commit){return versions.hasCommit(c,region,"",registered(source),commit);}
    private static String text(Object value) { return Objects.toString(value,""); }
    public Definition read(Connection c,String r,String comp,String pipeline) {
        try(var client=clients.client(c,r)) {
            var response=client.getBuildPipeline(GetBuildPipelineRequest.builder().buildPipelineId(pipeline).build());
            var p=response.getBuildPipeline();
            if(!comp.equals(p.getCompartmentId())) throw Errors.SCOPE.exception();
            String project=p.getProjectId();
            try { project=client.getProject(GetProjectRequest.builder().projectId(p.getProjectId()).build()).getProject().getName(); } catch(Exception ignored) {}
            var stages=new ArrayList<Stage>();var revisions=new TreeMap<String,String>();revisions.put(pipeline,response.getEtag());
            for(var summary:client.getPaginators().listBuildPipelineStagesRecordIterator(ListBuildPipelineStagesRequest.builder().buildPipelineId(pipeline).build())) {
                var stageResponse=client.getBuildPipelineStage(GetBuildPipelineStageRequest.builder().buildPipelineStageId(summary.getId()).build());
                var s=stageResponse.getBuildPipelineStage();
                if(!pipeline.equals(s.getBuildPipelineId()) || !comp.equals(s.getCompartmentId())) throw Errors.SCOPE.exception();
                if(s.getLifecycleState()==BuildPipelineStage.LifecycleState.Deleted) continue;
                revisions.put(s.getId(),stageResponse.getEtag());
                var sources=new ArrayList<Source>();var artifacts=new ArrayList<String>();
                String type=s.getClass().getSimpleName(),image="",spec="";int timeout=0;
                if(s instanceof BuildStage b) {
                    type="빌드·테스트";image=b.getImage()==null?"":b.getImage().getValue();spec=text(b.getBuildSpecFile());
                    if(spec.isEmpty()) spec="build_spec.yaml";
                    timeout=b.getStageExecutionTimeoutInSeconds()==null?0:b.getStageExecutionTimeoutInSeconds();
                    if(b.getBuildSourceCollection()!=null) for(var source:b.getBuildSourceCollection().getItems()) {
                        String repository=source instanceof DevopsCodeRepositoryBuildSource d?d.getRepositoryId():"";
                        String provider=repository.isEmpty()?source.getClass().getSimpleName().replace("BuildSource",""):"OCI";
                        sources.add(new Source(s.getId()+"::"+source.getName(),source.getName(),provider,repository,text(source.getRepositoryUrl()),text(source.getBranch()),Objects.equals(b.getPrimaryBuildSource(),source.getName())));
                    }
                } else if(s instanceof DeliverArtifactStage d) {
                    type="산출물 전달";
                    if(d.getDeliverArtifactCollection()!=null) for(var a:d.getDeliverArtifactCollection().getItems())
                        artifacts.add(a.getArtifactName()+" → "+a.getArtifactId());
                } else if(type.equals("TriggerDeploymentStage")) type="배포 호출";
                var after=s.getBuildPipelineStagePredecessorCollection()==null?List.<String>of():
                    s.getBuildPipelineStagePredecessorCollection().getItems().stream().map(BuildPipelineStagePredecessor::getId).toList();
                stages.add(new Stage(s.getId(),s.getDisplayName(),type,text(s.getLifecycleState().getValue()),after,image,spec,timeout,sources,artifacts));
            }
            // Topological display retains predecessor labels, including parallel branches.
            var pending=new ArrayList<>(stages);var ordered=new ArrayList<Stage>();var done=new HashSet<String>();done.add(pipeline);
            var labels=new HashMap<String,String>();labels.put(pipeline,"시작");stages.forEach(s->labels.put(s.id(),s.name()));
            while(!pending.isEmpty()) {
                var ready=pending.stream().filter(s->done.containsAll(s.after())).sorted(Comparator.comparing(Stage::name)).toList();
                if(ready.isEmpty()) throw Errors.BUILD_CHANGED.exception();
                for(var s:ready) {
                    ordered.add(new Stage(s.id(),s.name(),s.type(),s.state(),s.after().stream().map(labels::get).toList(),s.image(),s.spec(),s.timeout(),s.sources(),s.artifacts()));
                    done.add(s.id());pending.remove(s);
                }
            }
            String revision=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(revisions.toString().getBytes(StandardCharsets.UTF_8)));
            return new Definition(p.getDisplayName(),project,p.getLifecycleState().getValue(),text(p.getDescription()),revision,ordered);
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public List<Choice> branches(Connection c,String r,Source source) {
        if(source.repositoryId().isEmpty()) {
            try {var list=new ArrayList<>(versions.refs(c,r,"",registered(source)).stream().filter(v->v.kind().equals("BRANCH")).map(v->new Choice(v.name(),v.name())).toList());if(list.stream().noneMatch(v->v.value().equals(source.branch())))list.add(new Choice(source.branch(),source.branch()+" · OCI 설정"));return list;}
            catch(Exception e){return List.of(new Choice(source.branch(),source.branch()+" · OCI 설정"));}
        }
        try(var client=clients.client(c,r)) {
            var choices=new ArrayList<Choice>();
            for(var ref:client.getPaginators().listRefsRecordIterator(ListRefsRequest.builder().repositoryId(source.repositoryId()).refType(ListRefsRequest.RefType.Branch).build()))
                choices.add(new Choice(ref.getRefName(),ref.getRefName()));
            choices.sort(Comparator.comparing(Choice::value));return choices;
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public List<Choice> commits(Connection c,String r,Source source,String branch) {
        if(source.repositoryId().isEmpty()) {
            try{return versions.commits(c,r,"",registered(source),branch).stream().map(v->new Choice(v.commit(),v.commit().substring(0,12)+" · "+v.name())).toList();}
            catch(Exception e){return List.of();}
        }
        if(branches(c,r,source).stream().noneMatch(v->v.value().equals(branch))) throw Errors.BUILD_PARAMETERS.exception();
        try(var client=clients.client(c,r)) {
            return client.listCommits(ListCommitsRequest.builder().repositoryId(source.repositoryId()).refName(branch).limit(50).build())
                .getRepositoryCommitCollection().getItems().stream()
                .map(v->new Choice(v.getCommitId(),v.getCommitId().substring(0,Math.min(12,v.getCommitId().length()))+" · "+text(v.getCommitMessage()).lines().findFirst().orElse(""))).toList();
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public Specification specification(Connection c,String r,Source source,String path) {
        if(source.repositoryId().isEmpty()) return new Specification("","외부 저장소 파일은 해당 Git 서비스에서 확인하세요.",List.of(),List.of());
        try(var client=clients.client(c,r)) {
            var commits=client.listCommits(ListCommitsRequest.builder().repositoryId(source.repositoryId()).refName(source.branch()).limit(1).build()).getRepositoryCommitCollection().getItems();
            if(commits.isEmpty()) throw new IllegalStateException();
            {
                String content=readFile(client,source.repositoryId(),commits.getFirst().getCommitId(),path,65536);
                var options=new org.yaml.snakeyaml.LoaderOptions();options.setCodePointLimit(65536);options.setMaxAliasesForCollections(20);
                Object parsed=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options)).load(content);
                var outputs=new ArrayList<String>();var scripts=new ArrayList<String>();
                if(parsed instanceof Map<?,?> map) {
                    if(map.get("outputArtifacts") instanceof List<?> list) for(Object value:list) if(value instanceof Map<?,?> a)
                        outputs.add(text(a.get("name"))+" · "+text(a.get("type"))+" · "+text(a.get("location")));
                    if(map.get("steps") instanceof List<?> list) for(Object value:list) if(value instanceof Map<?,?> step)
                        scripts.add(text(step.get("name"))+"\n"+text(step.get("command")));
                }
                var sourceCommands=new ArrayList<String>();
                var scriptPaths=new LinkedHashSet<String>();
                for(var command:scripts) for(var line:command.lines().toList()) {
                    var match=java.util.regex.Pattern.compile("^(?:bash|sh) ([a-zA-Z0-9_./-]+\\.sh)$").matcher(line.trim());
                    if(match.matches() && !match.group(1).contains("..") && scriptPaths.size()<3) scriptPaths.add(match.group(1));
                }
                for(var scriptPath:scriptPaths) {
                    try {
                        String body=readFile(client,source.repositoryId(),commits.getFirst().getCommitId(),scriptPath,32768);
                        body.lines().filter(line->line.startsWith("git ") && (line.contains(" remote add ") || line.contains(" fetch ") || line.contains(" checkout ")))
                            .limit(12).forEach(line->sourceCommands.add(scriptPath+": "+line));
                    } catch(Exception ignored) { sourceCommands.add(scriptPath+": 소스 명령 조회 실패"); }
                }
                return new Specification(content,"기본 브랜치 "+source.branch()+"의 빌드 사양",outputs,scripts,sourceCommands);
            }
        } catch(Exception e) { return new Specification("","빌드 사양 조회 실패 · 저장소 읽기 권한과 파일 경로를 확인하세요.",List.of(),List.of()); }
    }
    private String readFile(com.oracle.bmc.devops.DevopsClient client,String repository,String ref,String path,int maximum) throws java.io.IOException {
        var object=client.getObject(GetObjectRequest.builder().repositoryId(repository).refName(ref).filePath(path).build()).getRepositoryObject();
        if(Boolean.TRUE.equals(object.getIsBinary()) || (object.getSizeInBytes()!=null && object.getSizeInBytes()>maximum)) throw new java.io.IOException();
        try(var stream=client.getObjectContent(GetObjectContentRequest.builder().repositoryId(repository).sha(object.getSha()).filePath(path).build()).getInputStream()) {
            var bytes=stream.readNBytes(maximum+1);if(bytes.length>maximum) throw new java.io.IOException();
            return new String(bytes,StandardCharsets.UTF_8);
        }
    }
}
