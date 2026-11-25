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
 private final OciBuildHistory clients;private final OciDeploymentSetup images;private final BuildSpecDraftStore store;
 private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
 private final Map<String,Run> active=new ConcurrentHashMap<>();
 public RemoteArmBuilds(@Value("${companion.oci-config}") String config,BuildSpecDraftStore store){this.clients=new OciBuildHistory(config);this.images=new OciDeploymentSetup(clients);this.store=store;}
 @jakarta.annotation.PreDestroy public void close(){executor.shutdownNow();}
 private String key(Connection c,String region,String compartment){return String.join("\n","arm-build-v1",c.profile(),c.tenancy(),c.user(),region,compartment);}
 public List<Builder> builders(Connection c,String region,String compartment){return store.read(key(c,region,compartment)+"\nbuilders").map(d->List.of(json.readValue(d.content(),Builder[].class))).orElse(List.of());}
 static void validate(Builder b){if(b.name()==null||!b.name().matches("[a-z0-9][a-z0-9-]{0,62}"))throw Errors.INVALID.exception();OkeApiAccess.validate(new OkeApiAccess.Settings("https://localhost","",b.host(),b.user(),b.keyPath()));if(b.host()==null||b.host().isBlank())throw Errors.INVALID.exception();}
 public synchronized void saveBuilder(Connection c,String region,String compartment,Builder builder){validate(builder);var list=new ArrayList<>(builders(c,region,compartment));list.removeIf(x->x.name().equals(builder.name()));if(list.size()>=16)throw Errors.INVALID.exception();list.add(builder);store.save(key(c,region,compartment)+"\nbuilders",new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(list),Instant.now().toString()));}
 static void validate(Input i){
  if(i.commit()==null||!i.commit().matches("[a-f0-9]{40}")||i.tag()==null||!i.tag().matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")||i.dockerfile()==null||!i.dockerfile().matches("[A-Za-z0-9_][A-Za-z0-9_./-]{0,200}")||Arrays.asList(i.dockerfile().split("/")).contains("..")||i.testCommand()==null||i.testCommand().isBlank()||i.testCommand().length()>8192||i.testCommand().contains("\u0000"))throw Errors.INVALID.exception();
 }
 public Plan preview(Connection c,String region,String compartment,Input i){
  validate(i);var builder=builders(c,region,compartment).stream().filter(b->b.name().equals(i.builder())).findFirst().orElseThrow(Errors.MISSING::exception);validate(builder);if(c.demo())throw Errors.SCOPE.exception();
  try(var client=clients.client(c,region);var registry=images.artifacts(c,region)){
   var repository=client.getRepository(com.oracle.bmc.devops.requests.GetRepositoryRequest.builder().repositoryId(i.sourceRepository()).build()).getRepository();if(!compartment.equals(repository.getCompartmentId()))throw Errors.SCOPE.exception();
   var commit=client.getCommit(GetCommitRequest.builder().repositoryId(i.sourceRepository()).commitId(i.commit()).build()).getRepositoryCommit();if(!i.commit().equals(commit.getCommitId()))throw Errors.SOURCE_READ.exception();
   var target=registry.getContainerRepository(GetContainerRepositoryRequest.builder().repositoryId(i.imageRepository()).build()).getContainerRepository();if(!compartment.equals(target.getCompartmentId())||!"AVAILABLE".equals(target.getLifecycleState().getValue()))throw Errors.SCOPE.exception();
   return new Plan(i,region+".ocir.io/"+target.getNamespace()+"/"+target.getDisplayName()+":"+i.tag(),repository.getName(),builder.name(),builder);
  }catch(io.github.devmrko.companion.error.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException(OciPipelineIam.error(e));}
 }
 public List<String> managedImages(){return Arrays.stream(com.oracle.bmc.devops.model.BuildStage.Image.values()).filter(x->x!=com.oracle.bmc.devops.model.BuildStage.Image.UnknownEnumValue).map(com.oracle.bmc.devops.model.BuildStage.Image::getValue).toList();}
 public synchronized String start(Connection c,String region,String compartment,Plan plan,String username,char[] password){
  if(active.values().stream().filter(x->x.state().equals("RUNNING")).count()>=4)throw Errors.CONFLICT.exception();
  var checked=preview(c,region,compartment,plan.input());if(!checked.equals(plan))throw Errors.CONFLICT.exception();
  if(username==null||username.length()>512||username.chars().anyMatch(Character::isISOControl)||password.length>8192||(password.length>0&&username.isBlank()))throw Errors.INVALID.exception();
  String id=UUID.randomUUID().toString(),identity=key(c,region,compartment)+"\n"+id;var builder=builders(c,region,compartment).stream().filter(b->b.name().equals(plan.input().builder())).findFirst().orElseThrow();
  var initial=new Run(id,"RUNNING","source",plan.image(),plan.input().imageRepository(),"","",Instant.now().toString(),"");save(identity,initial);
  char[] secret=password.clone();Arrays.fill(password,'\0');executor.submit(()->build(c,region,compartment,plan,builder,identity,initial,username,secret));return id;
 }
 private void save(String identity,Run run){active.put(identity,run);store.save(identity,new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(run),Instant.now().toString()));}
 private void phase(String identity,String phase){var r=active.get(identity);save(identity,new Run(r.id(),r.state(),phase,r.image(),r.repository(),r.digest(),r.error(),r.startedAt(),r.finishedAt()));}
 public Run read(Connection c,String region,String compartment,String id){
  if(!id.matches("[a-f0-9-]{36}"))throw Errors.INVALID.exception();String identity=key(c,region,compartment)+"\n"+id;var run=active.get(identity);if(run!=null)return run;
  run=store.read(identity).map(d->json.readValue(d.content(),Run.class)).orElseThrow(Errors.MISSING::exception);
  return run.state().equals("RUNNING")?new Run(run.id(),"UNKNOWN",run.phase(),run.image(),run.repository(),run.digest(),"앱 재시작으로 완료 여부를 확인하지 못했습니다. VM과 OCIR을 확인한 뒤 다시 실행하세요.",run.startedAt(),""):run;
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
 static String script(Plan plan,String work,String username,char[] password){
  String auth=password.length==0?"": "printf '%s' "+quote(new String(password))+" | podman login --authfile \"$work/auth.json\" --username "+quote(username)+" --password-stdin "+quote(plan.image().substring(0,plan.image().indexOf('/')))+"\nauth=(--authfile \"$work/auth.json\")\n";
  return """
set -euo pipefail
work=%s
trap 'rm -rf -- "$work"' EXIT
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
podman build "${auth[@]}" --platform linux/arm64 --file "$dockerfile" --tag %s .
stage push
podman push "${auth[@]}" --digestfile "$work/digest" %s
digest=$(cat "$work/digest")
[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]
printf 'COMPANION_DIGEST=%%s\\n' "$digest" >&3
""".formatted(quote(work),quote(plan.input().dockerfile()),auth,quote(plan.input().testCommand()),quote(plan.image()),quote(plan.image()));
 }
 private void build(Connection c,String region,String compartment,Plan plan,Builder builder,String identity,Run initial,String username,char[] password){
  Path source=null;String work="/tmp/companion-arm-"+initial.id();var digest=new java.util.concurrent.atomic.AtomicReference<String>("");
  try{
   source=Files.createTempFile("companion-arm-source-",".tar");
   try(var client=clients.client(c,region);var in=client.getRepositoryArchiveContent(GetRepositoryArchiveContentRequest.builder().repositoryId(plan.input().sourceRepository()).refName(plan.input().commit()).format("zip").build()).getInputStream()){ArmBuildSource.tar(in,source);}
   phase(identity,"transfer");command(builder,"umask 077; mkdir "+quote(work)+" && cat > "+quote(work+"/source.tar"),Files.newInputStream(source),120,x->{});
   String script=script(plan,work,username,password);Arrays.fill(password,'\0');
   command(builder,"bash -s",new ByteArrayInputStream(script.getBytes(java.nio.charset.StandardCharsets.UTF_8)),1800,line->{
    if(line.startsWith("COMPANION_PHASE=")){String step=line.substring(16);if(Set.of("architecture","source","test","build","push").contains(step))phase(identity,step);}
    if(line.matches("COMPANION_DIGEST=sha256:[a-f0-9]{64}"))digest.set(line.substring(17));
   });
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
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();var current=active.getOrDefault(identity,initial);save(identity,new Run(initial.id(),"FAILED",current.phase(),initial.image(),initial.repository(),digest.get(),PipelineIamRules.safe(e.getMessage()),initial.startedAt(),Instant.now().toString()));}
  finally{Arrays.fill(password,'\0');if(source!=null)try{Files.deleteIfExists(source);}catch(Exception ignored){}try{command(builder,"rm -rf -- "+quote(work),new ByteArrayInputStream(new byte[0]),15,x->{});}catch(Exception ignored){}}
 }
}
