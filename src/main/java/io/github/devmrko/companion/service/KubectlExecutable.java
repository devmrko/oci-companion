package io.github.devmrko.companion.service;

import io.github.devmrko.companion.port.BuildSpecDraftStore;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Only discovered kubectl locations may be selected; never accepts arbitrary commands. */
@Service
public class KubectlExecutable {
 private static final String KEY="companion-kubectl-executable-v1";
 private final String configured;
 private final BuildSpecDraftStore store;
 public KubectlExecutable(@Value("${companion.kubectl:/usr/local/bin/kubectl}") String configured,BuildSpecDraftStore store){this.configured=configured;this.store=store;}
 public String current(){return store.read(KEY).map(BuildSpecDraftStore.Draft::content).orElse(configured);}
 public static boolean available(String value){try{var p=Path.of(value);return p.isAbsolute()&&Files.isRegularFile(p)&&Files.isExecutable(p);}catch(Exception e){return false;}}
 public List<String> candidates(){
  var found=new LinkedHashSet<String>();
  for(String value:List.of("/opt/homebrew/bin/kubectl","/usr/local/bin/kubectl","/usr/bin/kubectl"))if(available(value))found.add(value);
  return List.copyOf(found);
 }
 public void apply(String candidate){if(!candidates().contains(candidate))throw Errors.INVALID.exception();store.save(KEY,new BuildSpecDraftStore.Draft("1",candidate,Instant.now().toString()));}
 public Readiness.Check check(){boolean ok=available(current());return new Readiness.Check("kubectl","kubectl",ok?Readiness.State.PASS:Readiness.State.FAIL,candidates().isEmpty()?Readiness.Permission.UNKNOWN:Readiness.Permission.CONFIRMED,ok?"실행 파일 확인":"설정된 실행 파일 없음","Companion의 kubectl 경로를 변경합니다.","Companion 서버에 kubectl을 설치한 뒤 다시 점검하세요.","","경로 적용");}
}
