package io.github.devmrko.companion.service;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.domain.Models.Connection;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class DefaultOkeRuntimePreparation implements OkeRuntimePreparation {
 private final OkeRuntimeResources resources;
 public DefaultOkeRuntimePreparation(OkeRuntimeResources resources){this.resources=resources;}
 public Report inspect(Connection c,String region,String compartment,OkeDeploymentSetup.Input i){
  if(List.of(i.configMap(),i.secret(),i.serviceAccount(),i.pullSecret(),i.ingress()).stream().allMatch(String::isBlank)&&!i.requireTls())return new Report(Instant.now(),List.of());
  try{return evaluate(resources.read(c,region,compartment,i.environment(),i.endpoint(),i.namespace()),i);}
  catch(io.github.devmrko.companion.error.BusinessException e){return new Report(Instant.now(),List.of(new Check("connection",State.UNKNOWN,"참조 조회 실패 · OCI 환경·클러스터·Kubernetes list 권한·API 접속 경로 확인")));}
 }
 static Report evaluate(OkeRuntimeResources.Snapshot snapshot,OkeDeploymentSetup.Input i){
  var checks=new ArrayList<Check>();
  reference(checks,snapshot,"configmaps",i.configMap(),Set.of());
  reference(checks,snapshot,"secrets",i.secret(),Set.of("Opaque"));
  reference(checks,snapshot,"serviceaccounts",i.serviceAccount(),Set.of());
  reference(checks,snapshot,"secrets",i.pullSecret(),Set.of("kubernetes.io/dockerconfigjson","kubernetes.io/dockercfg"));
  if(i.requireTls()&&i.ingress().isBlank())checks.add(new Check("tls",State.FAIL,"TLS 점검에는 Ingress 선택이 필요합니다."));
  if(!i.ingress().isBlank()){
   var listing=snapshot.resources().get("ingresses");
   if(listing==null||!listing.available())checks.add(new Check("ingress",State.UNKNOWN,"Ingress 조회 불가 · list 권한과 API 연결 확인"));
   else{
    var ingress=snapshot.ingresses().stream().filter(x->x.name().equals(i.ingress())).findFirst();
    if(ingress.isEmpty())checks.add(new Check("ingress",State.FAIL,"선택한 Ingress가 없습니다."));
    else{
     var v=ingress.get();var routes=v.routes().stream().filter(x->x.service().equals(i.app())).toList();
     if(routes.isEmpty()||routes.stream().anyMatch(x->!Set.of("http",String.valueOf(i.port())).contains(x.port())))checks.add(new Check("route",State.FAIL,"Ingress backend가 명세의 Service 이름·포트와 다릅니다."));
     else{
      checks.add(new Check("route",State.PASS,"Ingress → 명세 Service "+i.app()+":"+i.port()+" 일치"));
      for(var route:routes){
       var matches=v.tls().stream().filter(t->t.hosts().stream().anyMatch(h->covers(h,route.host()))&&!t.secret().isBlank()).toList();
       if(matches.isEmpty())checks.add(new Check("tls",i.requireTls()?State.FAIL:State.WARN,"호스트 "+route.host()+"의 TLS Secret 참조 없음"));
       else for(var tls:matches)reference(checks,snapshot,"secrets",tls.secret(),Set.of("kubernetes.io/tls"));
      }
     }
     checks.add(new Check("endpoint",v.addresses().isEmpty()?State.WARN:State.PASS,v.addresses().isEmpty()?"Ingress 주소 미할당 · 컨트롤러 상태 확인":"Ingress 주소 할당 확인 · DNS·HTTPS 통신·인증서 유효성은 별도"));
    }
   }
   var services=snapshot.resources().get("services");
   if(services==null||!services.available())checks.add(new Check("service",State.UNKNOWN,"기존 Service 유형 조회 불가"));
   else snapshot.services().stream().filter(s->s.name().equals(i.app())&&!"ClusterIP".equals(s.type())).findFirst().ifPresent(s->checks.add(new Check("service",State.FAIL,"기존 "+s.type()+" Service를 ClusterIP로 덮어쓸 수 없습니다. 다른 앱 이름 또는 기존 명세를 사용하세요.")));
  }
  return new Report(snapshot.observedAt(),List.copyOf(checks));
 }
 private static void reference(List<Check> checks,OkeRuntimeResources.Snapshot s,String kind,String name,Set<String> types){
  if(name.isBlank())return;String key=kind+"/"+name;var listing=s.resources().get(kind);
  if(listing==null||!listing.available()){checks.add(new Check(key,State.UNKNOWN,"목록 조회 불가 · "+kind+" list 권한과 API 연결 확인"));return;}
  var found=listing.items().stream().filter(x->x.name().equals(name)).findFirst();
  checks.add(new Check(key,found.isEmpty()?State.FAIL:!types.isEmpty()&&!types.contains(found.get().type())?State.FAIL:State.PASS,found.isEmpty()?"선택한 리소스 없음":!types.isEmpty()&&!types.contains(found.get().type())?"Secret 유형 불일치":"같은 Namespace의 참조 존재 확인 · 내용·사용 권한은 별도"));
 }
 static boolean covers(String pattern,String host){if(host.isBlank())return false;if(pattern.equals(host))return true;if(!pattern.startsWith("*."))return false;String suffix=pattern.substring(1);return host.endsWith(suffix)&&host.substring(0,host.length()-suffix.length()).matches("[^.]+");}
}
