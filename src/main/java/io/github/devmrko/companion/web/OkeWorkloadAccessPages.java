package io.github.devmrko.companion.web;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
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
public class OkeWorkloadAccessPages {
 private final CatalogPages catalog;
 private final DeploymentEnvironments environments;
 private final OkeWorkloadAccess access;
 public OkeWorkloadAccessPages(CatalogPages catalog,DeploymentEnvironments environments,OkeWorkloadAccess access){this.catalog=catalog;this.environments=environments;this.access=access;}
 record Ticket(Connection connection,String region,String compartment,Instant expires,OkeWorkloadAccess.Plan plan,boolean executed) {}
 @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession session){var items=(Map<String,Ticket>)session.getAttribute("okeAccessTickets");if(items==null){items=new LinkedHashMap<>();session.setAttribute("okeAccessTickets",items);}return items;}
 private String issue(HttpSession session,Connection c,CatalogPages.Selection scope,OkeWorkloadAccess.Plan plan){synchronized(session){var items=tickets(session);items.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(items.size()>=16)items.remove(items.keySet().iterator().next());String key=UUID.randomUUID().toString();items.put(key,new Ticket(c,scope.region(),scope.compartment(),Instant.now().plusSeconds(3600),plan,false));return key;}}
 private Ticket checked(Authentication auth,HttpSession session,String key,boolean consume){synchronized(session){var t=tickets(session).get(key);if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);if(c.demo()||!c.equals(t.connection())||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment())||session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();if(consume){if(t.executed())throw Errors.BUILD_CHANGED.exception();tickets(session).put(key,new Ticket(c,t.region(),t.compartment(),t.expires(),t.plan(),true));}return t;}}
 private void base(Authentication a,HttpServletRequest r,Model m){catalog.navigation(a,r,m);m.addAttribute("stateLabels",Map.of("GRANTED","접근 확인","DENIED_OR_NOT_FOUND","권한·대상 확인 필요","PENDING","실행 중","JOB_FAILED","검증 Pod 실패","AUTH_FAILURE","인증 실패","CONNECTION_FAILURE","연결 실패","API_FAILURE","OCI 호출 실패","UNKNOWN","미확인"));m.addAttribute("navSelection",catalog.selection(SecurityConfig.connection(a),r.getSession()));m.addAttribute("scopeBack","/deployments/oke/access");}
 @GetMapping("/deployments/oke/access") String page(Authentication a,HttpServletRequest r,Model m,@RequestParam(defaultValue="") String environment,@RequestParam(defaultValue="PUBLIC_ENDPOINT") String endpoint,@RequestParam(defaultValue="default") String namespace,@RequestParam(defaultValue="") String ticket){
  base(a,r,m);var c=SecurityConfig.connection(a);var s=catalog.selection(c,r.getSession());
  if(!ticket.isBlank()){var t=checked(a,r.getSession(),ticket,false);if(t.plan()==null)throw Errors.INVALID.exception();m.addAttribute("plan",t.plan());m.addAttribute("ticket",ticket);m.addAttribute("executed",t.executed());if(t.executed())m.addAttribute("result",access.result(c,s.region(),s.compartment(),t.plan()));return "oke-workload-access";}
  var input=new OkeWorkloadAccess.Input(environment,endpoint,namespace,"","");m.addAttribute("input",input);
  try{if(c.demo()||r.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();m.addAttribute("environments",environments.environments(c,s.region(),s.compartment()).stream().filter(e->e.type().equals("OKE")&&e.state().equals("ACTIVE")).toList());if(!environment.isBlank()){m.addAttribute("options",access.options(c,s.region(),s.compartment(),environment,endpoint,namespace));m.addAttribute("formTicket",issue(r.getSession(),c,s,null));}}
  catch(BusinessException e){m.addAttribute("setupError",e.getMessage());}return "oke-workload-access";
 }
 @PostMapping("/deployments/oke/access/prepare") String prepare(Authentication a,HttpSession s,@RequestParam String ticket,@ModelAttribute OkeWorkloadAccess.Input input){var t=checked(a,s,ticket,true);if(t.plan()!=null)throw Errors.INVALID.exception();var p=access.prepare(t.connection(),t.region(),t.compartment(),input);return "redirect:/deployments/oke/access?ticket="+issue(s,t.connection(),catalog.selection(t.connection(),s),p);}
 @PostMapping("/deployments/oke/access/execute") String execute(Authentication a,HttpSession s,@RequestParam String ticket,RedirectAttributes flash){var t=checked(a,s,ticket,true);if(t.plan()==null)throw Errors.INVALID.exception();try{access.execute(t.connection(),t.region(),t.compartment(),t.plan());}catch(BusinessException e){flash.addFlashAttribute("message",e.getMessage());}return "redirect:/deployments/oke/access?ticket="+ticket;}
 @PostMapping("/deployments/oke/access/remove") String remove(Authentication a,HttpSession s,@RequestParam String ticket,RedirectAttributes flash){var t=checked(a,s,ticket,false);if(t.plan()==null||!t.executed())throw Errors.INVALID.exception();access.remove(t.connection(),t.region(),t.compartment(),t.plan());synchronized(s){tickets(s).remove(ticket);}flash.addFlashAttribute("message","검증 Job 삭제 요청 완료");return "redirect:/deployments/oke/access";}
 @GetMapping("/deployments/oke/access/manifest.json") @ResponseBody org.springframework.http.ResponseEntity<String> download(Authentication a,HttpSession s,@RequestParam String ticket){var t=checked(a,s,ticket,false);if(t.plan()==null)throw Errors.INVALID.exception();return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=workload-access.json").contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(t.plan().manifest());}
 @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Workload access failed: {}",e.code(),e);return OperationErrors.view(e,catalog,a,r);}
}
