package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BackupPolicies;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @Profile("!legacy")
public class BackupPolicyPages {
 private final CatalogPages catalog;private final BackupPolicies policies;
 public BackupPolicyPages(CatalogPages catalog,BackupPolicies policies){this.catalog=catalog;this.policies=policies;}
 record Ticket(Connection c,String region,String compartment,BackupPolicies.Plan plan,Instant expires) {}
 @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("backupPolicyTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("backupPolicyTickets",m);}return m;}
 @GetMapping("/backups/policies") String page(Authentication a,HttpServletRequest r,Model m,@RequestParam(defaultValue="") String volume){
  catalog.navigation(a,r,m);var c=SecurityConfig.connection(a);var scope=catalog.selection(c,r.getSession());if(r.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();m.addAttribute("connection",c);m.addAttribute("navSelection",scope);m.addAttribute("scopeBack","/backups/policies");m.addAttribute("volumes",policies.volumes(c,scope.region(),scope.compartment()));m.addAttribute("volume",volume);
  var state=volume.isBlank()?null:policies.read(c,scope.region(),scope.compartment(),volume);var available=state==null?policies.policies(c,scope.region(),scope.compartment()):state.policies();m.addAttribute("state",state);m.addAttribute("policies",available);
  String current="없음";if(state!=null&&state.current()!=null){var id=state.current().policy();current=available.stream().filter(p->p.id().equals(id)).map(BackupPolicies.Policy::name).findFirst().orElse(id);}m.addAttribute("currentPolicy",current);return "backup-policies";
 }
 @PostMapping("/backups/policies/preview") String preview(Authentication a,HttpServletRequest r,Model m,@RequestParam String volume,@RequestParam String operation,@RequestParam(defaultValue="") String policy){
  page(a,r,m,volume);var c=SecurityConfig.connection(a);var scope=catalog.selection(c,r.getSession());var p=policies.plan(c,scope.region(),scope.compartment(),volume,operation,policy);
  synchronized(r.getSession()){var map=tickets(r.getSession());map.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(map.size()>=16)map.remove(map.keySet().iterator().next());String token=UUID.randomUUID().toString();map.put(token,new Ticket(c,scope.region(),scope.compartment(),p,Instant.now().plusSeconds(600)));m.addAttribute("ticket",token);}m.addAttribute("plan",p);var before=p.state().current();String current="없음";if(before!=null){current=before.policy();@SuppressWarnings("unchecked") var available=(List<BackupPolicies.Policy>)m.getAttribute("policies");current=available.stream().filter(item->item.id().equals(before.policy())).map(BackupPolicies.Policy::name).findFirst().orElse(current);}m.addAttribute("currentPolicy",current);return "backup-policies";
 }
 @PostMapping("/backups/policies/apply") String apply(Authentication a,HttpSession session,RedirectAttributes flash,@RequestParam String ticket){
  Ticket t;synchronized(session){t=tickets(session).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(a);var scope=catalog.selection(c,session);if(!c.equals(t.c())||session.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();
  policies.apply(c,t.region(),t.compartment(),t.plan());flash.addFlashAttribute("message","정책 변경 요청 완료 · OCI 연결 상태를 확인하세요.");flash.addAttribute("volume",t.plan().state().volume());return "redirect:/backups/policies";
 }
 @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
