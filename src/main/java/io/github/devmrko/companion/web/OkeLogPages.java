package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.OkePodLogs;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller @Profile("!legacy")
public class OkeLogPages {
 private final CatalogPages catalog;private final OkePodLogs logs;
 public OkeLogPages(CatalogPages catalog,OkePodLogs logs){this.catalog=catalog;this.logs=logs;}
 @GetMapping("/agents/connections/logs") String page(Authentication auth,HttpServletRequest req,Model model,@RequestParam String selected,@RequestParam(defaultValue="") String pod,@RequestParam(defaultValue="") String uid,@RequestParam(defaultValue="") String container,@RequestParam(defaultValue="false") boolean previous,@RequestParam(defaultValue="200") int lines,@RequestParam(defaultValue="") String region,@RequestParam(defaultValue="") String compartment){
  var c=SecurityConfig.connection(auth);catalog.navigation(auth,req,model);var s=catalog.selection(c,req.getSession());model.addAttribute("selection",s);model.addAttribute("scopeBack","/agents/connections");model.addAttribute("selected",selected);model.addAttribute("selectedPod",pod);model.addAttribute("selectedContainer",container);model.addAttribute("previous",previous);model.addAttribute("lines",lines);
  try{
   if(c.demo()||req.getSession().getAttribute("scopeInvalid")!=null)throw new IllegalArgumentException("실제 OCI 연결과 리전·구획을 선택하세요.");
   if((!region.isBlank()&&!region.equals(s.region()))||(!compartment.isBlank()&&!compartment.equals(s.compartment()))||(!pod.isBlank()&&(region.isBlank()||compartment.isBlank())))throw new IllegalArgumentException("리전·구획이 바뀌었습니다. 앱 상태에서 로그를 다시 여세요.");
   var choices=logs.choices(c,s.region(),s.compartment(),selected);model.addAttribute("choices",choices);
   if(!pod.isBlank())model.addAttribute("result",logs.read(c,s.region(),s.compartment(),selected,pod,uid,container,previous,lines));
  }catch(IllegalArgumentException e){model.addAttribute("logError",e.getMessage());}return "oke-logs";
 }
}
