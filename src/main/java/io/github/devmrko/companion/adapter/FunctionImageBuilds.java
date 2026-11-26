package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.functions.*;
import com.oracle.bmc.functions.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ArmBuilds;
import io.github.devmrko.companion.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.*;
import tools.jackson.databind.json.JsonMapper;

/** Reuses the ARM source/registry flow; only application image assembly runs in Functions. */
final class FunctionImageBuilds {
 record Target(String endpoint,String fingerprint,String sourceRepository,boolean automaticDependencies,String etag){}
 interface CacheObserver {void used(Connection c,String region,BuildCacheJournal.Use use);}
 private final OciBuildHistory clients;private final String crane;private final CacheObserver cacheObserver;
 private final JsonMapper json=JsonMapper.builder().build();
 FunctionImageBuilds(OciBuildHistory clients,String crane){this(clients,crane,(c,r,u)->{});}
 FunctionImageBuilds(OciBuildHistory clients,String crane,CacheObserver observer){this.clients=clients;this.crane=crane;this.cacheObserver=observer;}
 Target inspect(Connection c,String region,String compartment,String id,String image)throws Exception {
  try(var client=FunctionsManagementClient.builder().build(clients.authentication(c))){
   client.setRegion(region);var response=client.getFunction(GetFunctionRequest.builder().functionId(id).build());var f=response.getFunction();
   var app=client.getApplication(GetApplicationRequest.builder().applicationId(f.getApplicationId()).build()).getApplication();
   var config=new HashMap<String,String>();if(app.getConfig()!=null)config.putAll(app.getConfig());if(f.getConfig()!=null)config.putAll(f.getConfig());
   String repository=image.substring(0,image.lastIndexOf(':')),source=config.getOrDefault("SOURCE_REPOSITORY","");
   if(!compartment.equals(f.getCompartmentId())||!compartment.equals(app.getCompartmentId())||!"ACTIVE".equals(f.getLifecycleState().getValue())||!"GENERIC_ARM".equals(app.getShape().getValue())||!"sustena-layer-v1".equals(config.get("BUILD_RUNNER_CONTRACT"))||!repository.equals(config.get("IMAGE_REPOSITORY"))||!source.matches("[a-z0-9.-]+\\.ocir\\.io/[a-z0-9_-]+/[a-z0-9_./-]+")||!source.startsWith(image.substring(0,image.indexOf('/'))+"/")||source.contains("/../"))throw Errors.SCOPE.exception();
   boolean automatic="true".equals(config.get("AUTOMATIC_DEPENDENCIES"));
   if(automatic&&!config.getOrDefault("RUNTIME_BASE_IMAGE","").matches(java.util.regex.Pattern.quote(source)+"@sha256:[a-f0-9]{64}"))throw Errors.SCOPE.exception();
   return new Target(f.getInvokeEndpoint(),response.getEtag()+"/"+app.getTimeUpdated(),source,automatic,response.getEtag());
  }
 }
 static String sha(Path path)throws Exception {try(var in=Files.newInputStream(path)){var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;)digest.update(buffer,0,n);return HexFormat.of().formatHex(digest.digest());}}
 static void bundle(Path source,Path output,String commit)throws Exception {
  String prefix="";int best=Integer.MAX_VALUE;
  try(var in=new TarArchiveInputStream(Files.newInputStream(source))){for(var entry=in.getNextEntry();entry!=null;entry=in.getNextEntry()){String name=entry.getName();if(name.equals("pyproject.toml")||name.endsWith("/pyproject.toml")){int depth=name.split("/").length;if(depth<best){best=depth;prefix=name.substring(0,name.length()-"pyproject.toml".length());}}}}
  if(best==Integer.MAX_VALUE)throw Errors.SOURCE_READ.exception();
  long total=0;
  try(var in=new TarArchiveInputStream(Files.newInputStream(source));var out=new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(output)))){
   out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
   for(var entry=in.getNextEntry();entry!=null;entry=in.getNextEntry()){
    if(!entry.getName().startsWith(prefix)||!entry.isFile())continue;String name=entry.getName().substring(prefix.length());
    if(!Set.of("src","tests","vendor","docs","pyproject.toml","uv.lock","README.md").contains(name.split("/")[0]))continue;
    total+=entry.getSize();if(total>80L*1024*1024)throw Errors.SOURCE_READ.exception();var next=new TarArchiveEntry(name);next.setSize(entry.getSize());next.setMode(0644);out.putArchiveEntry(next);in.transferTo(out);out.closeArchiveEntry();
   }
   byte[] manifest=("{\"schema_version\":1,\"commit\":\""+commit+"\"}").getBytes(StandardCharsets.UTF_8);var next=new TarArchiveEntry("source.json");next.setSize(manifest.length);out.putArchiveEntry(next);out.write(manifest);out.closeArchiveEntry();
  }
  if(Files.size(output)>48L*1024*1024)throw Errors.SOURCE_READ.exception();
 }
 String build(Connection c,String region,String compartment,ArmBuilds.Plan plan,Path source,String username,char[] password)throws Exception {
  return build(c,region,compartment,plan,source,username,password,"");
 }
 String build(Connection c,String region,String compartment,ArmBuilds.Plan plan,Path source,String username,char[] password,String runId)throws Exception {
  var target=inspect(c,region,compartment,plan.target().functionId(),plan.image());if(!target.fingerprint().equals(plan.functionFingerprint()))throw Errors.BUILD_CHANGED.exception();
  Path root=Files.createTempDirectory("companion-function-image-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
  try{
   Path archive=root.resolve("source.tgz"),layer=root.resolve("source-layer.tar");bundle(source,archive,plan.input().commit());
   try(var out=new TarArchiveOutputStream(Files.newOutputStream(layer));var in=Files.newInputStream(archive)){var entry=new TarArchiveEntry("source.tgz");entry.setSize(Files.size(archive));out.putArchiveEntry(entry);in.transferTo(out);out.closeArchiveEntry();}
   String host=plan.image().substring(0,plan.image().indexOf('/'));String token=new String(password);
   Path auth=root.resolve("config.json");Files.writeString(auth,json.writeValueAsString(Map.of("auths",Map.of(host,Map.of("auth",Base64.getEncoder().encodeToString((username+":"+token).getBytes(StandardCharsets.UTF_8)))))));Files.setPosixFilePermissions(auth,PosixFilePermissions.fromString("rw-------"));
   // crane accepts the config directory explicitly; the token never enters command arguments.
   String sourceTag=target.sourceRepository()+":source-"+UUID.randomUUID();
   authCommand(List.of(crane,"append","-f",layer.toString(),"-t",sourceTag),root);
   String digest=authCommand(List.of(crane,"digest",sourceTag),root);
   if(!digest.matches("sha256:[a-f0-9]{64}"))throw Errors.HANDOFF.exception();
   var createdCacheImages=new HashSet<String>();
   var payload=new LinkedHashMap<String,Object>();payload.put("action","build");if(!runId.isBlank()){BuildReceipt.labels(runId,plan.input().commit());payload.put("buildId",runId);}payload.put("commit",plan.input().commit());payload.put("tag",plan.input().tag());payload.put("sourceImage",target.sourceRepository()+"@"+digest);payload.put("sourceSha256",sha(archive));payload.put("registryCredentials",Map.of("username",username,"password",token));
   var configuration=ClientConfiguration.builder().connectionTimeoutMillis(10000).readTimeoutMillis(330000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();
   try(var invoke=FunctionsInvokeClient.builder().configuration(configuration).endpoint(target.endpoint()).build(clients.authentication(c))){
    for(int attempt=0;attempt<2;attempt++){
     tools.jackson.databind.JsonNode result;
     var response=invoke.invokeFunction(InvokeFunctionRequest.builder().functionId(plan.target().functionId()).fnInvokeType(InvokeFunctionRequest.FnInvokeType.Sync).invokeFunctionBody(new OciFunctionBuilds.SignedBody(json.writeValueAsBytes(payload))).build());
     try(var body=response.getInputStream()){byte[] bytes=body.readNBytes(65537);if(bytes.length>65536)throw Errors.HANDOFF.exception();result=json.readTree(bytes);}
     System.getLogger(FunctionImageBuilds.class.getName()).log(System.Logger.Level.INFO,
       "Function result: status={0}, phase={1}, tests={2}, failures={3}, errors={4}, dependencyCacheHit={5}, elapsedSeconds={6}",
       result.path("status").asText(),result.path("phase").asText(),result.path("tests").path("tests").asInt(),result.path("tests").path("failures").asInt(),result.path("tests").path("errors").asInt(),result.path("dependencyCacheHit").asBoolean(),result.path("elapsedSeconds").asDouble());
     if("RUNTIME_REQUIRED".equals(result.path("status").asText())){
      if(attempt!=0||!target.automaticDependencies())throw Errors.HANDOFF.exception();
      validateRuntime(result,target.sourceRepository(),plan.input().commit());
      for(var created:result.path("createdImages"))if(Set.of(result.path("runtimeImage").asText(),result.path("dependencyImage").asText()).contains(created.asText()))createdCacheImages.add(created.asText().substring(created.asText().indexOf('@')+1));
      String runtimeDigest=authCommand(List.of(crane,"digest",result.path("runtimeTag").asText()),root);
      var runtimeConfig=json.readTree(authCommand(List.of(crane,"config",result.path("runtimeImage").asText()),root));
      if(!result.path("runtimeImage").asText().endsWith("@"+runtimeDigest)||!"arm64".equals(runtimeConfig.path("architecture").asText())||!"linux".equals(runtimeConfig.path("os").asText())||!result.path("lockHash").asText().equals(runtimeConfig.path("config").path("Labels").path("companion.lock").asText())||!"v2".equals(runtimeConfig.path("config").path("Labels").path("companion.cache").asText()))throw Errors.HANDOFF.exception();
      updateRuntime(c,region,compartment,plan,target,result);awaitRuntime(invoke,plan.target().functionId(),result.path("lockHash").asText());continue;
     }
     if(!"SUCCEEDED".equals(result.path("status").asText()))throw new IllegalStateException("Function "+result.path("phase").asText()+": "+PipelineIamRules.safe(result.path("error").asText().replace(token,"[REDACTED]")));
     String built=result.path("digest").asText(),repository=plan.image().substring(0,plan.image().lastIndexOf(':'));
     if(!built.matches("sha256:[a-f0-9]{64}")||!result.path("image").asText().equals(repository+"@"+built)||!result.path("commit").asText().equals(plan.input().commit())||result.path("tests").path("tests").asInt()<=0||result.path("tests").path("failures").asInt()!=0||result.path("tests").path("errors").asInt()!=0||!result.path("environment").path("arch").asText().equals("aarch64"))throw Errors.HANDOFF.exception();
     if(target.automaticDependencies()&&result.has("dependencyTag"))try{
      validateRuntime(result,target.sourceRepository(),plan.input().commit());
      String dependency=result.path("dependencyImage").asText(),runtime=result.path("runtimeImage").asText(),depTag=result.path("dependencyTag").asText();
      if(!depTag.matches(java.util.regex.Pattern.quote(target.sourceRepository())+":deps-[a-f0-9]{64}"))throw Errors.HANDOFF.exception();
      cacheObserver.used(c,region,new BuildCacheJournal.Use(target.sourceRepository(),result.path("lockHash").asText(),Map.of("deps",dependency.substring(dependency.indexOf('@')+1),"runtime",runtime.substring(runtime.indexOf('@')+1)),Map.of("deps",depTag.substring(depTag.lastIndexOf(':')+1),"runtime",result.path("runtimeTag").asText().substring(result.path("runtimeTag").asText().lastIndexOf(':')+1)),createdCacheImages));
     }catch(Exception ignored){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"캐시 사용 기록 저장 실패 · 미추적 이미지는 삭제하지 않습니다");}
     return built;
    }
    throw Errors.HANDOFF.exception();
   }
  }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
 String recover(ArmBuilds.Plan plan,String runId,String username,char[] password)throws Exception {
  Path root=Files.createTempDirectory("companion-build-recovery-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
  try{
   String host=plan.image().substring(0,plan.image().indexOf('/'));Path file=root.resolve("config.json");Files.writeString(file,json.writeValueAsString(Map.of("auths",Map.of(host,Map.of("auth",Base64.getEncoder().encodeToString((username+":"+new String(password)).getBytes(StandardCharsets.UTF_8)))))));Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
   String digest=authCommand(List.of(crane,"digest",plan.image()),root);if(!digest.matches("sha256:[a-f0-9]{64}"))throw Errors.HANDOFF.exception();
   String pinned=plan.image().substring(0,plan.image().lastIndexOf(':'))+"@"+digest;var config=json.readTree(authCommand(List.of(crane,"config",pinned),root));
   if(!BuildReceipt.matches(config,runId,plan.input().commit()))throw Errors.HANDOFF.exception();return digest;
  }finally{Arrays.fill(password,'\0');try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
 static void validateRuntime(tools.jackson.databind.JsonNode result,String repository,String commit){
  String quoted=java.util.regex.Pattern.quote(repository);
  if(!result.path("runtimeImage").asText().matches(quoted+"@sha256:[a-f0-9]{64}")||!result.path("runtimeTag").asText().matches(quoted+":runtime-[a-f0-9]{64}")||!result.path("dependencyImage").asText().matches(quoted+"@sha256:[a-f0-9]{64}")||!result.path("lockHash").asText().matches("[a-f0-9]{64}")||!commit.equals(result.path("commit").asText())||!"aarch64".equals(result.path("environment").path("arch").asText()))throw Errors.HANDOFF.exception();
 }
 private void updateRuntime(Connection c,String region,String compartment,ArmBuilds.Plan plan,Target approved,tools.jackson.databind.JsonNode result)throws Exception{
  var now=inspect(c,region,compartment,plan.target().functionId(),plan.image());if(!now.equals(approved))throw Errors.BUILD_CHANGED.exception();
  try(var management=FunctionsManagementClient.builder().build(clients.authentication(c))){
   management.setRegion(region);var before=management.getFunction(GetFunctionRequest.builder().functionId(plan.target().functionId()).build());
   if(!approved.etag().equals(before.getEtag()))throw Errors.BUILD_CHANGED.exception();
   String image=result.path("runtimeTag").asText(),digest=result.path("runtimeImage").asText().substring(result.path("runtimeImage").asText().indexOf('@')+1);
   image=runtimeImageAddress(region,image);
   management.updateFunction(UpdateFunctionRequest.builder().functionId(plan.target().functionId()).ifMatch(before.getEtag()).updateFunctionDetails(com.oracle.bmc.functions.model.UpdateFunctionDetails.builder().image(image).imageDigest(digest).build()).build());
   long deadline=System.nanoTime()+180_000_000_000L;
   while(System.nanoTime()<deadline){var f=management.getFunction(GetFunctionRequest.builder().functionId(plan.target().functionId()).build()).getFunction();String state=f.getLifecycleState().getValue();if(state.equals("ACTIVE")&&digest.equals(f.getImageDigest()))return;if(state.equals("FAILED")||state.equals("DELETED"))throw new IllegalStateException("Function 의존성 런타임 변경 실패: "+state);Thread.sleep(2000);}
   throw new IllegalStateException("Function 의존성 런타임 준비 시간 초과 · 자동 재호출하지 않습니다. 빌더 상태를 확인하세요.");
  }
 }
 private void awaitRuntime(FunctionsInvokeClient invoke,String function,String lock)throws Exception {
  long deadline=System.nanoTime()+180_000_000_000L;
  while(System.nanoTime()<deadline){
   var response=invoke.invokeFunction(InvokeFunctionRequest.builder().functionId(function).fnInvokeType(InvokeFunctionRequest.FnInvokeType.Sync).invokeFunctionBody(new OciFunctionBuilds.SignedBody("{\"action\":\"probe\"}".getBytes(StandardCharsets.UTF_8))).build());
   try(var body=response.getInputStream()){
    var value=json.readTree(body.readNBytes(65536));
    if(runtimeReady(value,lock))return;
   }
   Thread.sleep(2000);
  }
  throw new IllegalStateException("Function 런타임 의존성 전파 시간 초과 · 빌드는 재실행하지 않았습니다.");
 }
 static boolean runtimeReady(tools.jackson.databind.JsonNode value,String lock){
  return lock.matches("[a-f0-9]{64}")&&lock.equals(value.path("dependencyLock").asText())&&"aarch64".equals(value.path("arch").asText())&&"sustena-layer-v1".equals(value.path("contract").asText());
 }
 static String runtimeImageAddress(String region,String image){
  return com.oracle.bmc.Region.fromRegionId(region).getRegionCode()+".ocir.io"+image.substring(image.indexOf('/'));
 }
 private String authCommand(List<String> args,Path root)throws Exception {
  // Separate helper keeps registry authentication out of all application/global environment state.
  var process=new ProcessBuilder(args).redirectErrorStream(true);process.environment().put("DOCKER_CONFIG",root.toString());
  var p=process.start();try(var readers=Executors.newVirtualThreadPerTaskExecutor()){
   var output=readers.submit(()->{try(var in=p.getInputStream()){return in.readNBytes(131073);}});
   try{if(!p.waitFor(180,TimeUnit.SECONDS))throw new IOException("crane source upload timed out");byte[] value=output.get(5,TimeUnit.SECONDS);if(value.length>131072)throw new IOException("crane response exceeded limit");String text=new String(value,StandardCharsets.UTF_8).trim();if(p.exitValue()!=0)throw new IOException("crane: "+PipelineIamRules.safe(text));return text;}
   finally{p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();output.cancel(true);}
  }
 }
}
