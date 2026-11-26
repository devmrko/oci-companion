package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.artifacts.requests.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RemoteArmBuilds implements ArmBuilds {
 private final FunctionImageBuilds functionImages;private final BuildExecutionLocks locks;
 private final OciBuildHistory clients;private final OciDeploymentSetup images;private final BuildSpecDraftStore store;private final OkeRuntimeResources resources;
 private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
 private final Map<String,Run> active=new ConcurrentHashMap<>();
 public RemoteArmBuilds(String config,BuildSpecDraftStore store,OkeRuntimeResources resources){this(config,store,resources,"crane");}
 public RemoteArmBuilds(String config,BuildSpecDraftStore store,OkeRuntimeResources resources,String crane){this(config,store,resources,crane,".data/catalog");}
 @org.springframework.beans.factory.annotation.Autowired
 public RemoteArmBuilds(@Value("${companion.oci-config}") String config,BuildSpecDraftStore store,OkeRuntimeResources resources,@Value("${companion.crane:crane}") String crane,@Value("${companion.data-dir:.data/catalog}") String directory){this.locks=new BuildExecutionLocks(directory,store);this.resources=resources;this.clients=new OciBuildHistory(config);this.images=new OciDeploymentSetup(clients);this.store=store;this.functionImages=new FunctionImageBuilds(clients,crane);}
 @jakarta.annotation.PreDestroy public void close(){executor.shutdownNow();}
 private String key(Connection c,String region,String compartment){return String.join("\n","arm-build-v1",c.profile(),c.tenancy(),c.user(),region,compartment);}
 public List<Builder> builders(Connection c,String region,String compartment){return store.read(key(c,region,compartment)+"\nbuilders").map(d->List.of(json.readValue(d.content(),Builder[].class))).orElse(List.of());}
 static void validate(Builder b){if(b.name()==null||!b.name().matches("[a-z0-9][a-z0-9-]{0,62}"))throw Errors.INVALID.exception();if(b.kind().equals("FUNCTION")){if(!b.functionId().startsWith("ocid1.fnfunc.")||b.secretEnvironment().isBlank())throw Errors.INVALID.exception();}else {if(!b.kind().equals("VM"))throw Errors.INVALID.exception();OkeApiAccess.validate(new OkeApiAccess.Settings("https://localhost","",b.host(),b.user(),b.keyPath()));if(b.host()==null||b.host().isBlank())throw Errors.INVALID.exception();}
  if(!b.secretEnvironment().isBlank()){OciOkeWorkloadConnections.name(b.secretNamespace());OciOkeWorkloadConnections.name(b.secretName());OciOkeWorkloadConnections.endpoint(b.secretEndpoint());}
  else if(!b.secretNamespace().isBlank()||!b.secretName().isBlank())throw Errors.INVALID.exception();}
 public synchronized void saveBuilder(Connection c,String region,String compartment,Builder builder){validate(builder);var list=new ArrayList<>(builders(c,region,compartment));list.removeIf(x->x.name().equals(builder.name()));if(list.size()>=16)throw Errors.INVALID.exception();list.add(builder);store.save(key(c,region,compartment)+"\nbuilders",new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(list),Instant.now().toString()));}
 public synchronized void removeBuilder(Connection c,String region,String compartment,String name){var list=builders(c,region,compartment);if(list.stream().noneMatch(b->b.name().equals(name)))throw Errors.MISSING.exception();store.save(key(c,region,compartment)+"\nbuilders",new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(list.stream().filter(b->!b.name().equals(name)).toList()),Instant.now().toString()));}
 static void validate(Input i){
  if(i.commit()==null||!i.commit().matches("[a-f0-9]{40}")||i.tag()==null||!i.tag().matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")||i.dockerfile()==null||!i.dockerfile().matches("[A-Za-z0-9_][A-Za-z0-9_./-]{0,200}")||Arrays.asList(i.dockerfile().split("/")).contains("..")||i.testCommand()==null||i.testCommand().isBlank()||i.testCommand().length()>8192||i.testCommand().contains("\u0000"))throw Errors.INVALID.exception();
 }
 public Plan preview(Connection c,String region,String compartment,Input i){
  validate(i);var builder=builders(c,region,compartment).stream().filter(b->b.name().equals(i.builder())).findFirst().orElseThrow(Errors.MISSING::exception);validate(builder);if(c.demo())throw Errors.SCOPE.exception();
  try(var client=clients.client(c,region);var registry=images.artifacts(c,region)){
   var repository=client.getRepository(com.oracle.bmc.devops.requests.GetRepositoryRequest.builder().repositoryId(i.sourceRepository()).build()).getRepository();if(!compartment.equals(repository.getCompartmentId()))throw Errors.SCOPE.exception();
   var commit=client.getCommit(GetCommitRequest.builder().repositoryId(i.sourceRepository()).commitId(i.commit()).build()).getRepositoryCommit();if(!i.commit().equals(commit.getCommitId()))throw Errors.SOURCE_READ.exception();
   var target=registry.getContainerRepository(GetContainerRepositoryRequest.builder().repositoryId(i.imageRepository()).build()).getContainerRepository();if(!compartment.equals(target.getCompartmentId())||!"AVAILABLE".equals(target.getLifecycleState().getValue()))throw Errors.SCOPE.exception();
   String image=region+".ocir.io/"+target.getNamespace()+"/"+target.getDisplayName()+":"+i.tag();
   return new Plan(i,image,repository.getName(),builder.name(),builder,builder.kind().equals("FUNCTION")?functionImages.inspect(c,region,compartment,builder.functionId(),image).fingerprint():"");
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException(OciPipelineIam.error(e));}
 }
 public List<Run> pending(Connection c,String region,String compartment){return locks.pending(key(c,region,compartment)).stream().filter(id->store.read(key(c,region,compartment)+"\n"+id).isPresent()).map(id->read(c,region,compartment,id)).toList();}
 public List<String> managedImages(){return Arrays.stream(com.oracle.bmc.devops.model.BuildStage.Image.values()).filter(x->x!=com.oracle.bmc.devops.model.BuildStage.Image.UnknownEnumValue).map(com.oracle.bmc.devops.model.BuildStage.Image::getValue).toList();}
 public synchronized String start(Connection c,String region,String compartment,Plan plan,String username,char[] password){
  if(active.values().stream().filter(x->x.state().equals("RUNNING")).count()>=4)throw Errors.CONFLICT.exception();
  var checked=preview(c,region,compartment,plan.input());if(!checked.equals(plan))throw Errors.CONFLICT.exception();
  if(username==null||username.length()>512||username.chars().anyMatch(Character::isISOControl)||password.length>8192||(password.length>0&&username.isBlank()))throw Errors.INVALID.exception();
  String id=UUID.randomUUID().toString(),identity=key(c,region,compartment)+"\n"+id;var builder=builders(c,region,compartment).stream().filter(b->b.name().equals(plan.input().builder())).findFirst().orElseThrow();
  var initial=new Run(id,"RUNNING","source",plan.image(),plan.input().imageRepository(),"","",Instant.now().toString(),"");
  var lease=locks.acquire(BuildExecutionLocks.physical(c,region,builder),identity,false);boolean submitted=false;
  char[] secret=password.clone();
  try{
   store.save(identity+"\nplan",new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(plan),Instant.now().toString()));save(identity,initial);String login=username;
   if(secret.length==0&&!builder.secretEnvironment().isBlank())try(var credential=resources.pullCredentials(c,region,compartment,builder.secretEnvironment(),builder.secretEndpoint(),builder.secretNamespace(),builder.secretName(),plan.image().substring(0,plan.image().indexOf('/')))){login=credential.username();secret=credential.password();}
   final String loginUser=login;final char[] loginSecret=secret;executor.submit(()->build(c,region,compartment,plan,builder,identity,initial,loginUser,loginSecret,lease));submitted=true;return id;
  }finally{Arrays.fill(password,'\0');if(!submitted){Arrays.fill(secret,'\0');try{save(identity,new Run(id,"FAILED","preparation",initial.image(),initial.repository(),"","원격 실행 전 준비 실패",initial.startedAt(),Instant.now().toString()));lease.resolved();}finally{lease.close();}}}

 }
 private void save(String identity,Run run){store.save(identity,new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(run),Instant.now().toString()));active.put(identity,run);}
 private void phase(String identity,String phase){var r=active.get(identity);save(identity,new Run(r.id(),r.state(),phase,r.image(),r.repository(),r.digest(),r.error(),r.startedAt(),r.finishedAt()));}
 public Run read(Connection c,String region,String compartment,String id){
  if(!id.matches("[a-f0-9-]{36}"))throw Errors.INVALID.exception();String identity=key(c,region,compartment)+"\n"+id;var run=active.get(identity);if(run!=null)return run;
  run=store.read(identity).map(d->json.readValue(d.content(),Run.class)).orElseThrow(Errors.MISSING::exception);
  return run.state().equals("RUNNING")?new Run(run.id(),"UNKNOWN",run.phase(),run.image(),run.repository(),run.digest(),"앱 재시작으로 완료 여부를 확인하지 못했습니다. OCIR 완료 증거를 재확인하세요. 원격 종료 확인 전 새 실행은 차단합니다.",run.startedAt(),""):run;
 }
 public synchronized Run reconcile(Connection c,String region,String compartment,String id){
  var run=read(c,region,compartment,id);if(!run.state().equals("UNKNOWN"))return run;String identity=key(c,region,compartment)+"\n"+id;
  var plan=store.read(identity+"\nplan").map(d->json.readValue(d.content(),Plan.class)).orElseThrow(()->new IllegalStateException("이전 버전 실행에는 복구 증거가 없습니다 · 원격 실행을 직접 확인하세요"));
  try(var lease=locks.acquire(BuildExecutionLocks.physical(c,region,plan.target()),identity,true)){
   var b=plan.target();if(b.secretEnvironment().isBlank())throw new IllegalStateException("등록된 인증 참조가 없어 결과를 재확인할 수 없습니다");
   try(var auth=resources.pullCredentials(c,region,compartment,b.secretEnvironment(),b.secretEndpoint(),b.secretNamespace(),b.secretName(),plan.image().substring(0,plan.image().indexOf('/')))){
    String digest=functionImages.recover(plan,id,auth.username(),auth.password());
    var restored=new Run(id,"SUCCEEDED","recovered",run.image(),run.repository(),digest,"OCIR의 실행 ID·커밋·테스트 성공·ARM64 증거로 확인했습니다",run.startedAt(),Instant.now().toString());save(identity,restored);lease.resolved();return restored;
   }catch(Exception e){throw new IllegalStateException("완료 증거를 확인하지 못했습니다 · 빌드를 재실행하지 않았고 잠금을 유지합니다");}
  }
 }
 public synchronized void abandon(Connection c,String region,String compartment,String id){
  var run=read(c,region,compartment,id);if(!run.state().equals("UNKNOWN"))throw Errors.CONFLICT.exception();String identity=key(c,region,compartment)+"\n"+id;
  var plan=store.read(identity+"\nplan").map(d->json.readValue(d.content(),Plan.class)).orElseThrow(Errors.MISSING::exception);
  try(var lease=locks.acquire(BuildExecutionLocks.physical(c,region,plan.target()),identity,true)){
   save(identity,new Run(id,"ABANDONED",run.phase(),run.image(),run.repository(),run.digest(),"사용자가 원격 실행 종료 확인 후 포기했습니다 · 원격 중지나 성공 판정이 아닙니다",run.startedAt(),Instant.now().toString()));lease.resolved();
  }
 }
 static List<String> ssh(Builder b,String command){validate(b);return List.of("/usr/bin/ssh","-T","-o","BatchMode=yes","-o","StrictHostKeyChecking=yes","-o","ConnectTimeout=10","-i",b.keyPath(),b.user()+"@"+b.host(),command);}
 static String quote(String s){return "'"+s.replace("'","'\"'\"'")+"'";}
 void command(Builder builder,String command,InputStream input,int timeout,Consumer<String> line)throws Exception {
  var process=new ProcessBuilder(ssh(builder,command)).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  try(var io=Executors.newVirtualThreadPerTaskExecutor()){
   var writer=io.submit(()->{try(input;var out=process.getOutputStream()){input.transferTo(out);}return null;});
   var reader=io.submit(()->{try(var in=process.getInputStream()){var value=new StringBuilder();int total=0;for(int ch=in.read();ch!=-1;ch=in.read()){if(++total>131072)throw new IOException("원격 응답 한도 초과");if(ch=='\n'){line.accept(value.toString());value.setLength(0);}else {if(value.length()>1024)throw new IOException("원격 응답 형식 오류");value.append((char)ch);}}}return null;});
   try{if(!process.waitFor(timeout,TimeUnit.SECONDS))throw new IOException("원격 실행 시간 초과");writer.get(5,TimeUnit.SECONDS);reader.get(5,TimeUnit.SECONDS);if(process.exitValue()!=0)throw new IOException("원격 단계 실패 (exit "+process.exitValue()+")");}
   finally{process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();writer.cancel(true);reader.cancel(true);}
  }
 }
 static String script(Plan plan,String work,String username,char[] password){return script(plan,work,username,password,"");}
 static String script(Plan plan,String work,String username,char[] password,String runId){
  String receipt=runId.isBlank()?"":BuildReceipt.labels(runId,plan.input().commit()).entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->" --label "+quote(e.getKey()+"="+e.getValue())).collect(java.util.stream.Collectors.joining());
  String auth=password.length==0?"": "printf '%s' "+quote(new String(password))+" | podman login --authfile \"$work/auth.json\" --username "+quote(username)+" --password-stdin "+quote(plan.image().substring(0,plan.image().indexOf('/')))+"\nauth=(--authfile \"$work/auth.json\")\n";
  return """
set -euo pipefail
work=%s
finish(){
 code=$?
 if [ "$code" != 0 ]; then
  if grep -Eqi 'unauthorized|authentication required|invalid username|denied' "$work/build.log"; then printf 'COMPANION_ERROR=registry-auth\\n' >&3;
  elif grep -Eqi 'no space left|disk quota' "$work/build.log"; then printf 'COMPANION_ERROR=disk-space\\n' >&3;
  elif grep -Eqi 'certificate|TLS handshake' "$work/build.log"; then printf 'COMPANION_ERROR=tls\\n' >&3; fi
 fi
 rm -rf -- "$work"
}
trap finish EXIT
exec 3>&1
exec > "$work/build.log" 2>&1
stage(){ printf 'COMPANION_PHASE=%%s\\n' "$1" >&3; }
stage architecture
case "$(uname -m)" in aarch64|arm64) ;; *) exit 42;; esac
stage source
mkdir "$work/source"
tar -xf "$work/source.tar" -C "$work/source"
root="$work/source"
dockerfile=%s
if [ ! -f "$root/$dockerfile" ]; then
  folders=("$root"/*)
  [ "${#folders[@]}" = 1 ] && [ -d "${folders[0]}" ]
  root="${folders[0]}"
fi
cd "$root"
test -f "$dockerfile"
auth=()
%s
stage test
bash -o pipefail -c %s
stage build
podman build "${auth[@]}" --platform linux/arm64%s --file "$dockerfile" --tag %s .
stage push
if ! podman push "${auth[@]}" --digestfile "$work/digest" %s 2>"$work/push-error"; then
 printf 'COMPANION_PUSH_ERROR=' >&3
 tail -c 512 "$work/push-error" | base64 | tr -d '\\n' >&3
 printf '\\n' >&3
 cat "$work/push-error" >>"$work/build.log"
 exit 125
fi
digest=$(cat "$work/digest")
[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]
printf 'COMPANION_DIGEST=%%s\\n' "$digest" >&3
""".formatted(quote(work),quote(plan.input().dockerfile()),auth,quote(plan.input().testCommand()),receipt,quote(plan.image()),quote(plan.image()));
 }
 private void build(Connection c,String region,String compartment,Plan plan,Builder builder,String identity,Run initial,String username,char[] password,BuildExecutionLocks.Lease lease){
  boolean remoteStarted=false;Path source=null;String work="/tmp/companion-arm-"+initial.id();var digest=new java.util.concurrent.atomic.AtomicReference<String>("");var remoteError=new java.util.concurrent.atomic.AtomicReference<String>("");
  try{
   source=Files.createTempFile("companion-arm-source-",".tar");
   try(var client=clients.client(c,region);var in=client.getRepositoryArchiveContent(GetRepositoryArchiveContentRequest.builder().repositoryId(plan.input().sourceRepository()).refName(plan.input().commit()).format("zip").build()).getInputStream()){ArmBuildSource.tar(in,source);}
   remoteStarted=true;
   if(builder.kind().equals("FUNCTION")){phase(identity,"function");digest.set(functionImages.build(c,region,compartment,plan,source,username,password,initial.id()));}
   else {
   phase(identity,"transfer");command(builder,"umask 077; mkdir "+quote(work)+" && cat > "+quote(work+"/source.tar"),Files.newInputStream(source),120,x->{});
   String script=script(plan,work,username,password,initial.id());Arrays.fill(password,'\0');
   command(builder,"bash -s",new ByteArrayInputStream(script.getBytes(java.nio.charset.StandardCharsets.UTF_8)),1800,line->{
    if(line.startsWith("COMPANION_PHASE=")){String step=line.substring(16);if(Set.of("architecture","source","test","build","push").contains(step))phase(identity,step);}
    if(line.startsWith("COMPANION_PUSH_ERROR="))try{remoteError.set(PipelineIamRules.safe(new String(Base64.getDecoder().decode(line.substring(21)),java.nio.charset.StandardCharsets.UTF_8)));}catch(IllegalArgumentException ignored){}
    if(line.equals("COMPANION_ERROR=registry-auth"))remoteError.set("레지스트리 인증·push 권한 오류입니다. 등록한 OCIR 인증 Secret의 push 권한·호스트를 확인하거나 실행 미리보기에서 일회성 인증을 지정하세요.");
    if(line.equals("COMPANION_ERROR=disk-space"))remoteError.set("원격 VM의 디스크 공간이 부족합니다.");
    if(line.equals("COMPANION_ERROR=tls"))remoteError.set("원격 VM의 레지스트리 TLS 인증서 연결을 확인하세요.");
    if(line.matches("COMPANION_DIGEST=sha256:[a-f0-9]{64}"))digest.set(line.substring(17));
   });
   }
   if(!digest.get().matches("sha256:[a-f0-9]{64}"))throw new IllegalStateException("push 결과 digest를 확인하지 못했습니다.");
   phase(identity,"registry");boolean found=false;
   try(var registry=images.artifacts(c,region)){
    for(int attempt=0;attempt<6;attempt++){
     var listed=registry.listContainerImages(ListContainerImagesRequest.builder().compartmentId(compartment).repositoryId(plan.input().imageRepository()).imageDigest(digest.get()).limit(100).build()).getContainerImageCollection().getItems();
     if(listed.stream().anyMatch(image->digest.get().equals(image.getDigest()))){found=true;break;}Thread.sleep(5000);
    }
   }
   if(!found)throw new IllegalStateException("push 후 OCIR에서 digest를 아직 확인하지 못했습니다. 이미지 목록을 다시 조회하세요.");
   save(identity,new Run(initial.id(),"SUCCEEDED","complete",plan.image(),initial.repository(),digest.get(),"",initial.startedAt(),Instant.now().toString()));
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();var current=active.getOrDefault(identity,initial);save(identity,new Run(initial.id(),remoteStarted?"UNKNOWN":"FAILED",current.phase(),initial.image(),initial.repository(),digest.get(),PipelineIamRules.safe(e.getMessage())+(remoteError.get().isEmpty()?"":" · "+remoteError.get()),initial.startedAt(),Instant.now().toString()));}
  finally{Arrays.fill(password,'\0');if(source!=null)try{Files.deleteIfExists(source);}catch(Exception ignored){}try{if(!active.get(identity).state().equals("UNKNOWN")&&builder.kind().equals("VM"))command(builder,"rm -rf -- "+quote(work),new ByteArrayInputStream(new byte[0]),15,x->{});}catch(Exception ignored){}try{if(Set.of("SUCCEEDED","FAILED").contains(active.get(identity).state()))lease.resolved();}finally{lease.close();}}
 }
}
