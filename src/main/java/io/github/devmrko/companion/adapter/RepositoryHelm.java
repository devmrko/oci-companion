package io.github.devmrko.companion.adapter;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local rendering only: isolated configuration, no install, plugins, dependency downloads or cluster credentials. */
@Component
public class RepositoryHelm {
 private final String executable;
 public RepositoryHelm(@Value("${companion.helm:helm}") String executable){this.executable=executable;}
 static List<String> roots(Map<String,?> files){
  var roots=files.keySet().stream().filter(p->p.equals("Chart.yaml")||p.endsWith("/Chart.yaml")).map(p->p.substring(0,p.length()-10)).sorted(Comparator.comparingInt(String::length)).toList();
  var result=new ArrayList<String>();for(String root:roots)if(result.stream().noneMatch(root::startsWith))result.add(root);return result;
 }
 public Map<String,String> render(Map<String,String> files,String release,String namespace){
  var roots=roots(files);if(roots.isEmpty())return files;if(roots.size()!=1)throw new IllegalArgumentException("Helm 루트 차트가 여러 개입니다 · 배포 차트를 하나로 정리하세요");
  OciOkeWorkloadConnections.name(release);OciOkeWorkloadConnections.name(namespace);
  Path dir=null;Process process=null;
  try{
   dir=Files.createTempDirectory("companion-helm-");Path chart=Files.createDirectories(dir.resolve("chart")),home=Files.createDirectories(dir.resolve("home"));String prefix=roots.getFirst();
   for(var e:files.entrySet())if(e.getKey().startsWith(prefix)){String relative=e.getKey().substring(prefix.length());Path target=chart.resolve(relative).normalize();if(!target.startsWith(chart)||relative.isBlank())throw new IllegalArgumentException("Helm 파일 경로 오류");Files.createDirectories(target.getParent());Files.writeString(target,e.getValue());}
   Path kube=dir.resolve("kubeconfig");Files.writeString(kube,"apiVersion: v1\nkind: Config\nclusters: []\ncontexts: []\nusers: []\n");
   var command=List.of(executable,"template",release,chart.toString(),"--namespace",namespace,"--dry-run=client","--no-hooks","--skip-tests","--skip-schema-validation","--kubeconfig",kube.toString());
   var builder=new ProcessBuilder(command).directory(dir.toFile());builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin:/opt/homebrew/bin:/usr/local/bin");builder.environment().put("HOME",home.toString());
   for(String key:List.of("HELM_CONFIG_HOME","HELM_CACHE_HOME","HELM_DATA_HOME","HELM_PLUGINS"))builder.environment().put(key,Files.createDirectories(home.resolve(key)).toString());
   process=builder.start();final Process running=process;
   try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
    var out=pool.submit(()->bounded(running.getInputStream()));var err=pool.submit(()->bounded(running.getErrorStream()));
    if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalArgumentException("Helm 렌더링 시간 제한(30초) 초과");}
    String rendered=out.get(5,TimeUnit.SECONDS);err.get(5,TimeUnit.SECONDS);
    if(process.exitValue()!=0)throw new IllegalArgumentException("Helm 렌더링 실패 · 차트 의존성을 저장소에 포함하고 values 기본값을 확인하세요 (종료 코드 "+process.exitValue()+")");
    var result=new LinkedHashMap<String,String>();for(var e:files.entrySet())if(!e.getKey().startsWith(prefix))result.put(e.getKey(),e.getValue());
    // Keep Dockerfile/application hints even when the chart is at repository root.
    files.forEach((k,v)->{if(k.endsWith("Dockerfile")||k.matches(".*(?:application|bootstrap)[^/]*\\.(?:properties|ya?ml)$"))result.put(k,v);});
    result.put(prefix+"helm-rendered.yaml",rendered);return result;
   }
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Helm을 실행할 수 없습니다 · companion.helm 실행 경로와 차트를 확인하세요",e);}
  finally{if(process!=null&&process.isAlive())process.destroyForcibly();if(dir!=null)try(var paths=Files.walk(dir)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}catch(Exception ignored){}}
 }
 private static String bounded(java.io.InputStream stream)throws java.io.IOException{try(stream){byte[] data=stream.readNBytes(1024*1024+1);if(data.length>1024*1024)throw new java.io.IOException("Helm output limit");return new String(data,StandardCharsets.UTF_8);}}
}
