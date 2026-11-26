package io.github.devmrko.companion.web;
import io.github.devmrko.companion.adapter.NodePortServices;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentEnvironments;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @org.springframework.context.annotation.Profile("!legacy")
public class NodePortServicePages {
 private final CatalogPages catalog;private final DeploymentEnvironments environments;private final NodePortServices services;
 public NodePortServicePages(CatalogPages catalog,DeploymentEnvironments environments,NodePortServices services){this.catalog=catalog;this.environments=environments;this.services=services;}
 record Ticket(Connection actor,String region,String compartment,NodePortServices.Plan plan,String key,Instant expires){}
 private CatalogPages.Selection base(Authentication a,HttpServletRequest r,Model m){var c=SecurityConfig.connection(a);catalog.navigation(a,r,m);var s=catalog.selection(c,r.getSession());if(c.demo()||r.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();m.addAttribute("environments",environments.environments(c,s.region(),s.compartment()).stream().filter(e->e.type().equals("OKE")&&e.state().equals("ACTIVE")).toList());m.addAttribute("scopeBack","/environments/oke/nodeport/service");return s;}
 @GetMapping("/environments/oke/nodeport/service") String page(Authentication a,HttpServletRequest r,Model m){base(a,r,m);return "nodeport-service";}
 @PostMapping("/environments/oke/nodeport/service/preview") String preview(Authentication a,HttpServletRequest r,Model m,@ModelAttribute NodePortServices.Input input){var s=base(a,r,m);var c=SecurityConfig.connection(a);try{var plan=services.preview(c,s.region(),s.compartment(),input);var ticket=new Ticket(c,s.region(),s.compartment(),plan,UUID.randomUUID().toString(),Instant.now().plusSeconds(600));r.getSession().setAttribute("nodePortServiceTicket",ticket);m.addAttribute("plan",plan);m.addAttribute("ticket",ticket.key());}catch(Exception e){String error=PipelineIamRules.safe(e.getMessage());m.addAttribute("message",error);var permission=error.contains("(Forbidden)")||error.contains("NotAuthorized (403)")?Readiness.Permission.DENIED:Readiness.Permission.UNKNOWN;m.addAttribute("checkFailure",new Readiness.Check("nodeport","NodePort Service",Readiness.State.UNKNOWN,permission,error,"","관리자에게 대상 Namespace의 deployments get·services create 권한과 클러스터 전체 services list 권한을 요청하세요.\nkubectl auth can-i create services -n <namespace>\nkubectl auth can-i list services --all-namespaces","",""));}m.addAttribute("input",input);return "nodeport-service";}
 @PostMapping("/environments/oke/nodeport/service/create") String create(Authentication a,HttpSession s,RedirectAttributes flash,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){Ticket t;synchronized(s){t=(Ticket)s.getAttribute("nodePortServiceTicket");s.removeAttribute("nodePortServiceTicket");}var c=SecurityConfig.connection(a);var scope=catalog.selection(c,s);if(!confirmed||t==null||!ticket.equals(t.key())||t.expires().isBefore(Instant.now())||!c.equals(t.actor())||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment())||s.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();try{services.create(c,t.region(),t.compartment(),t.plan());flash.addFlashAttribute("message","Service를 생성했습니다. 보안 규칙과 외부 프록시는 별도 점검하세요.");}catch(Exception e){flash.addFlashAttribute("message",PipelineIamRules.safe(e.getMessage()));}return "redirect:/environments/oke/nodeport/service";}
}
