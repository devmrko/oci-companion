package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.OkePodLogs;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
@Component
public class KubectlPodLogs implements OkePodLogs {
 private final OkeWorkloadHealth reader;
 public KubectlPodLogs(OkeWorkloadHealth reader){this.reader=reader;}
 private OkeWorkloadHealth.Target target(Connection c,String r,String comp,String app)throws Exception{var t=reader.target(c,r,comp,app);if(t==null)throw new IllegalArgumentException();return t;}
 Choices choices(OkeWorkloadHealth.Target t)throws Exception{
  var deployment=reader.read(t,"deployment",t.deployment());var sets=reader.read(t,"replicasets",null);var pods=reader.read(t,"pods",null);
  var summary=OkeWorkloadHealth.summarize(t,deployment,sets,pods);var result=new ArrayList<Pod>();
  for(var p:summary.pods()){
   if(p.uid().isBlank())continue;OciOkeWorkloadConnections.name(p.name());
   for(var item:pods.path("items"))if(p.uid().equals(item.path("metadata").path("uid").asText()))result.add(new Pod(p.name(),p.uid(),containers(item)));
  }
  return new Choices(t.namespace(),t.deployment(),List.copyOf(result));
 }
 static List<String> containers(JsonNode pod){var names=new ArrayList<String>();for(String group:List.of("containers","initContainers"))for(var x:pod.path("spec").path(group)){String n=x.path("name").asText();OciOkeWorkloadConnections.name(n);names.add(n);}return List.copyOf(names);}
 public Choices choices(Connection c,String r,String comp,String app){try{return choices(target(c,r,comp,app));}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalArgumentException("Pod 목록 조회 실패 · OKE 연결과 Deployment/ReplicaSet/Pod 읽기 권한을 확인하세요.");}}
 static void samePod(OkeWorkloadHealth.Target t,JsonNode p,String name,String uid,String container){if(!"Pod".equals(p.path("kind").asText())||!t.namespace().equals(p.path("metadata").path("namespace").asText())||!name.equals(p.path("metadata").path("name").asText())||!uid.equals(p.path("metadata").path("uid").asText())||!containers(p).contains(container))throw new IllegalArgumentException();}
 public Result read(Connection c,String r,String comp,String app,String pod,String uid,String container,boolean previous,int lines){
  try{
   if(!Set.of(100,200,500).contains(lines)||uid==null||uid.isBlank())throw new IllegalArgumentException();OciOkeWorkloadConnections.name(pod);OciOkeWorkloadConnections.name(container);
   var t=target(c,r,comp,app);var available=choices(t);var selected=available.pods().stream().filter(p->p.name().equals(pod)&&p.uid().equals(uid)&&p.containers().contains(container)).findFirst().orElseThrow();
   samePod(t,reader.read(t,"pod",pod),pod,uid,container);
   var args=new ArrayList<>(List.of("logs",selected.name(),"--container="+container,"--tail="+lines,"--limit-bytes=65536","--timestamps=true","--pod-running-timeout=3s"));if(previous)args.add("--previous=true");
   byte[] bytes=reader.command(t,args,65536);
   samePod(t,reader.read(t,"pod",pod),pod,uid,container);
   String text=new String(bytes,StandardCharsets.UTF_8).replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]","");
   return new Result("조회 완료",text,"최근 "+lines+"줄 · 최대 64KiB · "+(previous?"직전 컨테이너 실행":"현재 컨테이너 실행")+" · 조회 시점의 로그이며 별도 저장하지 않습니다.",Instant.now().toString());
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return new Result("조회 불가","","Pod 교체·컨테이너 이름·pods/log get 권한·API 연결을 확인하세요. 직전 로그는 이전 실행이 보존된 경우에만 조회됩니다.",Instant.now().toString());}
 }
}
