package io.github.devmrko.companion.service;
import io.github.devmrko.companion.port.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Presentation of observed evidence. No execution authorization or inferred permissions. */
public final class Readiness {
 private Readiness() {}
 public enum State { PASS, WARN, FAIL, UNKNOWN, NA;
  public String label(){return switch(this){case PASS->"충족";case WARN->"확인 필요";case FAIL->"조치 필요";case UNKNOWN->"미확인";case NA->"해당 없음";};}
 }
 public record Item(String key,String name,String requirement,boolean needed,State state,String note,String href,String action){}
 public record Summary(List<Item> items,long required,long satisfied,long action,long unknown){}
 public static Summary summarize(List<Item> items){return new Summary(List.copyOf(items),items.stream().filter(Item::needed).count(),items.stream().filter(i->i.needed()&&i.state()==State.PASS).count(),items.stream().filter(i->i.state()==State.FAIL||i.state()==State.WARN).count(),items.stream().filter(i->i.needed()&&i.state()==State.UNKNOWN).count());}
 private static String link(String tab,String pipeline){return "/builds?tab="+tab+"&pipeline="+URLEncoder.encode(pipeline,StandardCharsets.UTF_8);}
 public static Summary prepare(BuildDefinition.Definition definition,BuildSetup.Settings settings,Map<String,BuildDefinition.Specification> specs,String pipeline){
  var out=new ArrayList<Item>();String config=link("pipelines",pipeline),execute=link("execute",pipeline);
  var stages=definition.stages().stream().filter(s->!s.spec().isBlank()).toList();
  boolean hasSources=!stages.isEmpty()&&stages.stream().allMatch(s->!s.sources().isEmpty());
  out.add(new Item("source","소스 저장소","필수",true,hasSources?State.PASS:State.FAIL,hasSources?"소스 연결 확인 · 접근 권한은 별도":"빌드 단계의 소스 필요",config+(hasSources?"#build-source-area":"#build-stage-create"),"소스 설정"));
  for(var check:BuildChecks.inspect(definition,settings,specs)){
   boolean storage=check.name().startsWith("저장"),inactive=storage&&check.state().equals("없음");
   State state=switch(check.state()){case "확인"->State.PASS;case "누락"->State.FAIL;case "오류"->check.message().contains("불일치")?State.FAIL:State.UNKNOWN;default->inactive?State.NA:State.UNKNOWN;};
   if(storage&&state==State.PASS&&stages.stream().anyMatch(s->!specs.containsKey(s.id())||specs.get(s.id()).content().isBlank()))state=State.UNKNOWN;
   String name=check.name().equals("단계 상태")?"빌드 설정":check.name().equals("실행 권한·로그")?"권한·로그":check.name().replace("명세 · ","빌드 명세 · ").replace("저장 위치","산출물 저장");
   String note=storage&&state==State.PASS?"출력 이름·저장소 연결 확인":storage&&state==State.UNKNOWN&&check.state().equals("확인")?"저장 위치 연결 · 명세 출력 미확인":inactive?"빌드·테스트만 실행 · 저장 생략":check.name().equals("실행 권한·로그")?"조회 권한과 실행 주체 권한은 별도":check.message().equals("ACTIVE 빌드 단계")?"실행 가능한 빌드 단계":check.message();
   out.add(new Item(storage?"storage":check.name().startsWith("명세")?"spec":check.name().equals("단계 상태")?"build":"permissions",name,storage?"조건부 필수":"필수",!inactive,state,note,check.name().equals("실행 권한·로그")?execute+"#permissions":config+(storage?"#build-storage-setup":check.name().startsWith("명세")?"#build-specification-form":state==State.FAIL?"#build-stage-create":"#build-pipeline-setup"),check.name().equals("실행 권한·로그")?"권한 안내":"설정 확인"));
  }
  boolean deploy=BuildChecks.purpose(definition).equals("deploy");
  out.add(new Item("deploy","배포 대상","조건부 필수",deploy,deploy?State.UNKNOWN:State.NA,deploy?"대상·런타임·네트워크 확인 필요":"배포 호출 없음","/environments","배포 대상 확인"));
  return summarize(out);
 }
 public static Summary deployment(DeploymentRuns.Plan plan,BuildHandoff.Proof proof,boolean selectedBuild,List<DeploymentImages.Check> images,List<DeploymentImages.Check> architectures){
  var out=new ArrayList<Item>();
  out.add(new Item("target","배포 대상·명세","필수",true,plan==null?State.UNKNOWN:State.PASS,plan==null?"대상 조회·명세 확인 필요":"대상 상태·지원 명세 확인","#deployment-preflight","점검 상세"));
  out.add(new Item("artifact","빌드·배포본 일치","조건부 필수",selectedBuild,proof!=null?State.PASS:selectedBuild?State.UNKNOWN:State.NA,proof!=null?proof.summary():selectedBuild?"선택 빌드의 배포본 확인 필요":"파이프라인의 기존 배포본 사용","#deployment-preflight","점검 상세"));
  var all=new ArrayList<DeploymentImages.Check>();if(images!=null)all.addAll(images);if(architectures!=null)all.addAll(architectures);
  boolean needsImages=plan==null||"OKE".equals(plan.targetType());
  State state=!needsImages?State.NA:all.stream().anyMatch(i->i.state()==DeploymentImages.State.BLOCKED)?State.FAIL:all.isEmpty()||images==null||architectures==null||all.stream().anyMatch(i->i.state()==DeploymentImages.State.UNKNOWN)?State.UNKNOWN:State.PASS;
  out.add(new Item("image","이미지·아키텍처","조건부 필수",needsImages,state,!needsImages?"파일 배포":state==State.PASS?"이미지·아키텍처 조회 조건 확인":"이미지·아키텍처 점검 필요","#deployment-preflight","점검 상세"));
  out.add(new Item("permissions","실행 권한","필수",true,State.UNKNOWN,"조회 성공과 실행 권한은 별도","/guides#permissions","권한 안내"));
  out.add(new Item("runtime","런타임·네트워크","필수",true,State.UNKNOWN,"실제 통신·앱 응답 확인 필요","/environments","배포 대상 확인"));
  return summarize(out);
 }
 public static Summary unknown(String message){return summarize(List.of(new Item("query","준비 상태","필수",true,State.UNKNOWN,message,"/guides#permissions","연결·권한 안내")));}
 private static State resultState(String state){return switch(state){case "SUCCEEDED"->State.PASS;case "FAILED"->State.FAIL;case "CANCELED"->State.WARN;default->State.UNKNOWN;};}
 public static boolean fileHandoff(BuildHistory.Detail detail){
  return detail.state().equals("SUCCEEDED")&&detail.storageState().equals("SUCCEEDED")&&detail.artifacts().stream().anyMatch(a->a.kind().equals("Artifact Registry"));
 }
 public static String deploymentLink(BuildHistory.Detail detail,String pipeline){
  return (fileHandoff(detail)||BuildImageIdentity.inspect(detail).stream().anyMatch(BuildImageIdentity.Image::verified))?"/deployments/runs?sourcePipeline="+URLEncoder.encode(pipeline,StandardCharsets.UTF_8)+"&sourceRun="+URLEncoder.encode(detail.id(),StandardCharsets.UTF_8):"/deployments";
 }
 public static Summary result(BuildHistory.Detail detail,String pipeline){
  boolean storage=detail.steps().stream().anyMatch(s->s.type().equals("DELIVER"));
  return summarize(List.of(
   new Item("build","빌드","필수",true,resultState(detail.buildState()),"OCI 빌드 단계 · "+detail.buildState(),"#build-logs","로그 보기"),
   new Item("tests","테스트·품질","선택",false,State.UNKNOWN,"보고서 조회 전","#build-report-form","보고서 보기"),
   new Item("storage","산출물 저장","조건부 필수",storage,storage?resultState(detail.storageState()):State.NA,storage?"OCI 전달 단계 · "+detail.storageState():"전달 단계 없음","#build-artifacts","산출물 보기"),
   new Item("deploy","배포 준비","조건부 필수",false,State.UNKNOWN,"배포 대상에서 별도 점검",deploymentLink(detail,pipeline),"배포 설정")
  ));
 }
}
