package io.github.devmrko.companion.adapter;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OkeTokenCache {
 private record Token(String value,Instant expires) {public String toString(){return "OKE token [redacted]";}}
 private final Map<String,Token> tokens=new LinkedHashMap<>();
 private final String config,oci;
 private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 public OkeTokenCache(@Value("${companion.oci-config}") String config,@Value("${companion.oci-cli:oci}") String oci){this.config=config;this.oci=oci;}
 byte[] generate(OkeWorkloadHealth.Target t)throws Exception {
  var auth=OciProfiles.load(config,t.profile());if(!auth.getTenantId().equals(t.tenancy())||!auth.getUserId().equals(t.user()))throw new IllegalArgumentException();
  var process=new ProcessBuilder(oci,"ce","cluster","generate-token","--cluster-id",t.clusterId(),"--region",t.region(),"--profile",t.profile(),"--config-file",config,"--auth",OciProfiles.cliAuth(config,t.profile())).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var output=executor.submit(()->{try(var stream=process.getInputStream()){return stream.readNBytes(131073);}});
   try{byte[] value=output.get(30,TimeUnit.SECONDS);if(value.length>131072||!process.waitFor(1,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalArgumentException();return value;}
   finally{process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();output.cancel(true);}
  }
 }
 private synchronized String token(OkeWorkloadHealth.Target t)throws Exception {
  String key=String.join("\n",t.profile(),t.tenancy(),t.user(),t.region(),t.clusterId());Instant now=Instant.now();var current=tokens.get(key);
  if(current!=null&&current.expires().isAfter(now.plusSeconds(60)))return current.value();
  byte[] content=generate(t);
  try{
   var status=json.readTree(content).path("status");String value=status.path("token").asText("");Instant expiry=Instant.parse(status.path("expirationTimestamp").asText(""));
   if(value.isBlank()||value.length()>65536||value.chars().anyMatch(Character::isWhitespace)||!expiry.isAfter(now.plusSeconds(60)))throw new IllegalArgumentException();
   tokens.entrySet().removeIf(e->!e.getValue().expires().isAfter(now));if(tokens.size()>=64)tokens.remove(tokens.keySet().iterator().next());
   tokens.put(key,new Token(value,expiry.isBefore(now.plusSeconds(600))?expiry:now.plusSeconds(600)));return value;
  }finally{Arrays.fill(content,(byte)0);}
 }
 /** Returns a disposable kubeconfig, or null for administrator-managed formats. */
 Path temporaryConfig(OkeWorkloadHealth.Target t)throws Exception {
  var source=Path.of(t.kubeconfig());if(Files.size(source)>262144)throw new IllegalArgumentException();
  tools.jackson.databind.JsonNode doc;try{doc=json.readTree(Files.readString(source));}catch(Exception format){return null;}
  if(!"selected".equals(doc.path("current-context").asText())||!"profile".equals(doc.path("users").path(0).path("name").asText())||!oci.equals(doc.path("users").path(0).path("user").path("exec").path("command").asText()))return null;
  String token=token(t);var mutable=(tools.jackson.databind.node.ObjectNode)doc;
  mutable.putArray("users").addObject().put("name","profile").putObject("user").put("token",token);
  var file=Files.createTempFile("companion-oke-token-",".json",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
  try{Files.writeString(file,json.writeValueAsString(mutable));return file;}catch(Exception e){Files.deleteIfExists(file);throw e;}
 }
}
