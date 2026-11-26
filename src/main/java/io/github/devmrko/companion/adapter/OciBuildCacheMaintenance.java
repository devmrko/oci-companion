package io.github.devmrko.companion.adapter;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.functions.*;
import com.oracle.bmc.functions.requests.*;
import com.oracle.bmc.identity.*;
import com.oracle.bmc.identity.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ArmBuilds;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Fail closed: incomplete inventory, changed evidence or a pending build prevents pruning. */
final class OciBuildCacheMaintenance {
 private final OciBuildHistory clients;private final BuildCacheJournal journal;private final BuildExecutionLocks locks;private final Clock clock;
 private final JsonMapper json=JsonMapper.builder().build();
 OciBuildCacheMaintenance(OciBuildHistory c,BuildCacheJournal j,BuildExecutionLocks l){this(c,j,l,Clock.systemUTC());}
 OciBuildCacheMaintenance(OciBuildHistory c,BuildCacheJournal j,BuildExecutionLocks l,Clock clock){clients=c;journal=j;locks=l;this.clock=clock;}
 record Candidate(String id,String etag,String digest,String tag){}
 record Snapshot(ArmBuilds.CachePlan plan,String key,List<Candidate> candidates){}
 ArmBuilds.CachePlan preview(Connection c,String region,String compartment,ArmBuilds.Builder builder){try(var gate=locks.maintenance()){return snapshot(c,region,compartment,builder).plan();}}
 void apply(Connection c,String region,String compartment,ArmBuilds.Builder builder,ArmBuilds.CachePlan approved,boolean automatic){
  try(var gate=locks.maintenance()){
   var current=snapshot(c,region,compartment,builder);if(!current.plan().equals(approved))throw new IllegalStateException("캐시 미리보기 이후 참조·사용 기록이 바뀌었습니다 · 다시 확인하세요");
   try(var registry=new OciDeploymentSetup(clients).artifacts(c,region)){
    // Recheck all references before each irreversible deletion, not merely the requested tag.
    for(var candidate:current.candidates()){
     var latest=snapshot(c,region,compartment,builder);if(latest.candidates().stream().noneMatch(x->x.equals(candidate)))throw new IllegalStateException("캐시 삭제 직전 보호 조건이 바뀌었습니다 · 정리를 중단합니다");
     registry.deleteContainerImage(DeleteContainerImageRequest.builder().imageId(candidate.id()).ifMatch(candidate.etag()).build());
    }
   }
   var state=journal.read(current.key());var deleted=current.candidates().stream().map(Candidate::digest).collect(java.util.stream.Collectors.toSet());
   journal.save(current.key(),new BuildCacheJournal.State(state.entries().stream().filter(e->!deleted.contains(e.digest())).toList(),automatic));
  }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("캐시 정리 실패 · 일부 삭제됐을 수 있으므로 미리보기를 다시 조회하세요");}
 }
 void afterSuccess(Connection c,String region,String compartment,ArmBuilds.Builder builder){
  if(!builder.kind().equals("FUNCTION"))return;
  try {
   try(var functions=FunctionsManagementClient.builder().build(clients.authentication(c))){functions.setRegion(region);var f=functions.getFunction(GetFunctionRequest.builder().functionId(builder.functionId()).build()).getFunction();var app=functions.getApplication(GetApplicationRequest.builder().applicationId(f.getApplicationId()).build()).getApplication();var settings=new HashMap<String,String>();if(app.getConfig()!=null)settings.putAll(app.getConfig());if(f.getConfig()!=null)settings.putAll(f.getConfig());if(!journal.read(journal.key(c.tenancy(),region,settings.getOrDefault("SOURCE_REPOSITORY",""))).automatic())return;}
   var p=preview(c,region,compartment,builder);if(p.automatic()&&p.items().stream().anyMatch(ArmBuilds.CacheItem::deletable))apply(c,region,compartment,builder,p,true);}
  catch(Exception e){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"자동 캐시 정리를 생략했습니다 · 캐시 미리보기에서 확인하세요");}
 }
 Snapshot snapshot(Connection c,String region,String compartment,ArmBuilds.Builder builder){
  if(!builder.kind().equals("FUNCTION")||c.demo())throw new IllegalStateException("실제 ARM Function 빌더에서만 캐시를 관리합니다");
  try(var functions=FunctionsManagementClient.builder().build(clients.authentication(c));var identity=IdentityClient.builder().build(clients.authentication(c));var registry=new OciDeploymentSetup(clients).artifacts(c,region)){
   functions.setRegion(region);identity.setRegion(region);
   var target=functions.getFunction(GetFunctionRequest.builder().functionId(builder.functionId()).build()).getFunction();
   if(!compartment.equals(target.getCompartmentId()))throw new IllegalStateException("빌더 구획이 일치하지 않습니다");
   var app=functions.getApplication(GetApplicationRequest.builder().applicationId(target.getApplicationId()).build()).getApplication();
   var config=new HashMap<String,String>();if(app.getConfig()!=null)config.putAll(app.getConfig());if(target.getConfig()!=null)config.putAll(target.getConfig());
   String repository=config.getOrDefault("SOURCE_REPOSITORY","");if(!repository.matches("[a-z0-9.-]+\\.ocir\\.io/[a-z0-9_-]+/[a-z0-9_./-]+")||!"sustena-layer-v1".equals(config.get("BUILD_RUNNER_CONTRACT")))throw new IllegalStateException("빌더 캐시 저장소를 확인하지 못했습니다");
   String path=repository.substring(repository.indexOf('/')+1),name=path.substring(path.indexOf('/')+1),key=journal.key(c.tenancy(),region,repository);var state=journal.read(key);
   var protectedDigests=new HashSet<String>();var protectedTags=new HashSet<String>();var referenceEvidence=new TreeSet<String>();
   var compartments=new TreeSet<String>();compartments.add(c.tenancy());
   for(var comp:identity.getPaginators().listCompartmentsRecordIterator(ListCompartmentsRequest.builder().compartmentId(c.tenancy()).compartmentIdInSubtree(true).accessLevel(ListCompartmentsRequest.AccessLevel.Any).build()))if(comp.getLifecycleState()==com.oracle.bmc.identity.model.Compartment.LifecycleState.Active)compartments.add(comp.getId());
   for(String comp:compartments){
    for(var summary:functions.getPaginators().listApplicationsRecordIterator(ListApplicationsRequest.builder().compartmentId(comp).build())){
     var application=functions.getApplication(GetApplicationRequest.builder().applicationId(summary.getId()).build());referenceEvidence.add(application.getEtag());protect(application.getApplication().getConfig(),path,protectedDigests,protectedTags);
     for(var item:functions.getPaginators().listFunctionsRecordIterator(ListFunctionsRequest.builder().applicationId(summary.getId()).build())){
      var response=functions.getFunction(GetFunctionRequest.builder().functionId(item.getId()).build());var f=response.getFunction();referenceEvidence.add(response.getEtag());if(f.getImageDigest()!=null)protectedDigests.add(f.getImageDigest());protect(f.getConfig(),path,protectedDigests,protectedTags);
     }
    }
   }
   var all=new ArrayList<com.oracle.bmc.artifacts.model.ContainerImageSummary>();String page=null;
   do{var response=registry.listContainerImages(ListContainerImagesRequest.builder().compartmentId(compartment).repositoryName(name).limit(100).page(page).build());all.addAll(response.getContainerImageCollection().getItems());page=response.getOpcNextPage();}while(page!=null);
   var protectedLocks=state.entries().stream().filter(e->protectedDigests.contains(e.digest())||protectedTags.contains(e.tag())).map(BuildCacheJournal.Entry::lock).collect(java.util.stream.Collectors.toSet());
   var eligible=BuildCacheJournal.eligible(state,clock.instant());var items=new ArrayList<ArmBuilds.CacheItem>();var candidates=new ArrayList<Candidate>();
   for(var entry:state.entries()){
    var versions=all.stream().filter(i->entry.digest().equals(i.getDigest())).toList();boolean referenced=protectedLocks.contains(entry.lock());
    boolean exact=versions.size()==1&&entry.tag().equals(versions.getFirst().getVersion())&&"AVAILABLE".equals(versions.getFirst().getLifecycleState().getValue());
    boolean delete=eligible.contains(entry.digest())&&!referenced&&exact;
    String reason=!entry.owned()?"생성 이력 없음 · 보존":referenced?"현재 Function 참조 · 보존":!eligible.contains(entry.digest())?"최근 3종·사용 30일·복구 7일 보호":!exact?"태그·이미지 상태 변경 또는 조회 불일치 · 보존":"미사용 30일 경과 · 삭제 가능";
    items.add(new ArmBuilds.CacheItem(entry.digest(),entry.tag(),entry.lastUsed(),delete,reason));
    if(delete){var response=registry.getContainerImage(GetContainerImageRequest.builder().imageId(versions.getFirst().getId()).build());var image=response.getContainerImage();var source=registry.getContainerRepository(GetContainerRepositoryRequest.builder().repositoryId(image.getRepositoryId()).build()).getContainerRepository();if(!path.equals(source.getNamespace()+"/"+source.getDisplayName()))throw new IllegalStateException("캐시 저장소 namespace 불일치");if(!entry.digest().equals(image.getDigest())||!entry.tag().equals(image.getVersion()))throw new IllegalStateException("캐시 이미지가 조회 중 변경됐습니다");candidates.add(new Candidate(image.getId(),response.getEtag(),entry.digest(),entry.tag()));}
   }
   items.sort(Comparator.comparing(ArmBuilds.CacheItem::tag));candidates.sort(Comparator.comparing(Candidate::id));
   String fingerprint=BuildExecutionLocks.hash(json.writeValueAsString(List.of(state,items,candidates,referenceEvidence)));
   return new Snapshot(new ArmBuilds.CachePlan(builder.name(),repository,List.copyOf(items),fingerprint,state.automatic()),key,List.copyOf(candidates));
  }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("캐시 참조·이미지 전체 조회 실패 · 삭제하지 않았습니다: "+OciPipelineIam.error(e));}
 }
 static void protect(Map<String,String> config,String path,Set<String> digests,Set<String> tags){
  if(config==null)return;for(String value:config.values()){
   var matcher=java.util.regex.Pattern.compile("sha256:[a-f0-9]{64}").matcher(value);while(matcher.find())digests.add(matcher.group());
   int index=value.indexOf('/'+path+':');if(index>=0)tags.add(value.substring(index+path.length()+2));
  }
 }
}
