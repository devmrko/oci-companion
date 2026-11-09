package io.github.devmrko.companion.web;
import io.github.devmrko.companion.adapter.AgentConnectionRegistry;
import io.github.devmrko.companion.adapter.AgentHealth;
import io.github.devmrko.companion.adapter.OciAgentDependencies;
import io.github.devmrko.companion.adapter.OkeWorkloadHealth;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @Profile("!legacy")
public class AgentConnectionPages {
 private final CatalogPages catalog;private final AgentConnectionRegistry registry;private final AgentHealth health;private final OciAgentDependencies dependencies;private final OkeWorkloadHealth workload;
 public AgentConnectionPages(CatalogPages catalog,AgentConnectionRegistry registry,AgentHealth health,OciAgentDependencies dependencies,OkeWorkloadHealth workload){this.catalog=catalog;this.registry=registry;this.health=health;this.dependencies=dependencies;this.workload=workload;}
 @GetMapping("/agents/connections") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String selected,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="false") boolean refresh,@RequestParam(defaultValue="0") int podPage,@RequestParam(defaultValue="0") int nodePage){
  var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var s=catalog.selection(c,request.getSession());model.addAttribute("scopeBack","/agents/connections");model.addAttribute("selection",s);model.addAttribute("selected",selected);model.addAttribute("refresh",refresh);
  if(c.demo()||request.getSession().getAttribute("scopeInvalid")!=null){model.addAttribute("connectionError","실제 OCI 연결과 리전·구획을 선택하세요.");return "agent-connections";}
  try {var all=registry.list(c,s.region(),s.compartment());int last=Math.max(0,(all.size()-1)/20);int current=Math.max(0,Math.min(page,last));model.addAttribute("items",all.subList(current*20,Math.min(all.size(),current*20+20)));model.addAttribute("page",current);model.addAttribute("last",last);model.addAttribute("total",all.size());model.addAttribute("credentials",registry.credentials(c,s.region(),s.compartment()));
   if(!selected.isBlank()){model.addAttribute("chosen",registry.get(c,s.region(),s.compartment(),selected));model.addAttribute("health",health.check(c,s.region(),s.compartment(),selected));model.addAttribute("dependencies",registry.get(c,s.region(),s.compartment(),selected).dependencies().stream().map(ref->dependencies.check(c,s.region(),s.compartment(),ref)).toList());var w=workload.check(c,s.region(),s.compartment(),selected,nodePage);model.addAttribute("workload",w);int pl=Math.max(0,(w.pods().size()-1)/20);int pp=Math.max(0,Math.min(podPage,pl));model.addAttribute("podPage",pp);model.addAttribute("podLast",pl);model.addAttribute("pods",w.pods().subList(pp*20,Math.min(w.pods().size(),pp*20+20)));}
  }catch(IllegalArgumentException e){model.addAttribute("connectionError",e.getMessage());}
  return "agent-connections";
 }
 @PostMapping("/agents/connections") String add(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String region,@RequestParam String compartment,@RequestParam String name,@RequestParam String environment,@RequestParam String address,@RequestParam String credential){
  try {var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(c.demo()||session.getAttribute("scopeInvalid")!=null||!region.equals(s.region())||!compartment.equals(s.compartment()))throw new IllegalArgumentException("리전·구획이 바뀌었습니다. 다시 확인하세요.");var id=registry.register(c,region,compartment,name,environment,address,credential);flash.addFlashAttribute("message","앱 연결을 등록했습니다.");return "redirect:/agents/connections?selected="+id;}
  catch(IllegalArgumentException e){flash.addFlashAttribute("message",e.getMessage());return "redirect:/agents/connections";}
 }

 @PostMapping("/agents/connections/dependency") String dependency(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String region,@RequestParam String compartment,@RequestParam String selected,@RequestParam String type,@RequestParam String label,@RequestParam String ocid,@RequestParam(defaultValue="false") boolean remove){
  String target="redirect:/agents/connections";
  try{var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(c.demo()||session.getAttribute("scopeInvalid")!=null||!region.equals(s.region())||!compartment.equals(s.compartment()))throw new IllegalArgumentException("리전·구획을 다시 확인하세요.");
   var app=registry.get(c,region,compartment,selected);target+="?selected="+app.id();var ref=new AgentConnectionRegistry.Dependency(type,label,ocid);
   if(!remove&&!dependencies.check(c,region,compartment,ref).verified())throw new IllegalArgumentException("현재 구획에서 조회 가능한 OCI 리소스만 등록할 수 있습니다.");
   registry.dependency(c,region,compartment,app.id(),ref,remove);flash.addFlashAttribute("message",remove?"참조를 해제했습니다. OCI 리소스는 변경되지 않습니다.":"의존 리소스를 등록했습니다.");
  }catch(IllegalArgumentException e){flash.addFlashAttribute("message",e.getMessage());}return target;
 }
}
