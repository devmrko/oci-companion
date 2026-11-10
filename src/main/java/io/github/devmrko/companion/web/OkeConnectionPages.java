package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.OkeWorkloadConnections;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller @Profile("!legacy")
public class OkeConnectionPages {
 private final OkeToolReadiness tools;
 private final CatalogPages catalog;private final OkeWorkloadConnections connections;
 public OkeConnectionPages(CatalogPages catalog,OkeWorkloadConnections connections,OkeToolReadiness tools){this.catalog=catalog;this.connections=connections;this.tools=tools;}
 @GetMapping("/agents/connections/oke") String page(Authentication auth,HttpServletRequest req,Model model,@RequestParam(defaultValue="") String selected,@RequestParam(defaultValue="") String cluster){
  model.addAttribute("okeTools",tools.tools());
  var c=SecurityConfig.connection(auth);catalog.navigation(auth,req,model);var scope=catalog.selection(c,req.getSession());model.addAttribute("selection",scope);model.addAttribute("scopeBack","/agents/connections/oke");
  try{if(c.demo()||req.getSession().getAttribute("scopeInvalid")!=null)throw new IllegalArgumentException("실제 OCI 프로파일과 리전·구획을 선택하세요.");var available=connections.clusters(c,scope.region(),scope.compartment());if(!model.containsAttribute("settings")){var current=connections.current(c,scope.region(),scope.compartment(),selected);if(selected.isBlank()&&!cluster.isBlank()&&available.stream().anyMatch(x->x.id().equals(cluster)))current=new OkeWorkloadConnections.Settings("","","",cluster,"PUBLIC_ENDPOINT","","",false);model.addAttribute("settings",current);}model.addAttribute("clusters",available);}
  catch(Exception e){model.addAttribute("setupError","OKE 연결 정보를 읽지 못했습니다. 현재 범위의 클러스터 조회 권한과 연결 설정을 확인하세요.");}return "oke-connection";
 }
 public record Options(List<String> items,String error){}
 @GetMapping("/agents/connections/oke/options") @ResponseBody Options options(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam String cluster,@RequestParam String endpoint,@RequestParam(defaultValue="") String namespace){
  try{var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(c.demo()||session.getAttribute("scopeInvalid")!=null||!region.equals(s.region())||!compartment.equals(s.compartment()))throw new IllegalArgumentException("리전·구획이 바뀌었습니다. 화면을 다시 여세요.");
   return new Options(namespace.isBlank()?connections.namespaces(c,region,compartment,cluster,endpoint):connections.deployments(c,region,compartment,cluster,endpoint,namespace),"");
  }catch(IllegalArgumentException e){return new Options(List.of(),e.getMessage());}
 }
 @PostMapping("/agents/connections/oke/remove") String remove(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String region,@RequestParam String compartment,@RequestParam String selected,@RequestParam(defaultValue="false") boolean confirmed){
  try{var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(!confirmed||c.demo()||session.getAttribute("scopeInvalid")!=null||!region.equals(s.region())||!compartment.equals(s.compartment()))throw new IllegalArgumentException("현재 범위와 연결 해제 확인을 확인하세요.");connections.remove(c,region,compartment,selected);flash.addFlashAttribute("message","조회 연결과 현재 kubeconfig를 해제했습니다. 클라우드 앱은 유지됩니다.");}
  catch(IllegalArgumentException e){flash.addFlashAttribute("message",e.getMessage());}return "redirect:/agents/connections";
 }
 @PostMapping("/agents/connections/oke") String save(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="") String selected,@RequestParam String name,@RequestParam String environment,@RequestParam String cluster,@RequestParam String endpoint,@RequestParam String namespace,@RequestParam String deployment,@RequestParam(defaultValue="false") boolean inspectNodes){
  try{var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(c.demo()||session.getAttribute("scopeInvalid")!=null||!region.equals(s.region())||!compartment.equals(s.compartment()))throw new IllegalArgumentException("리전·구획이 바뀌었습니다. 다시 선택하세요.");
   var id=connections.save(c,region,compartment,new OkeWorkloadConnections.Settings(selected,name,environment,cluster,endpoint,namespace,deployment,inspectNodes));flash.addFlashAttribute("message","OKE 조회 연결을 저장했습니다.");return "redirect:/agents/connections?selected="+id;
  }catch(IllegalArgumentException e){flash.addFlashAttribute("message",e.getMessage());flash.addFlashAttribute("settings",new OkeWorkloadConnections.Settings(selected,name,environment,cluster,endpoint,namespace,deployment,inspectNodes));return "redirect:/agents/connections/oke";}
 }
}
