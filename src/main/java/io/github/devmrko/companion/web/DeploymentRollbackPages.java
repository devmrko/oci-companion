package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentRollback;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller @Profile("!legacy")
public class DeploymentRollbackPages {
 private final CatalogPages catalog;private final DeploymentRollback rollback;
 public DeploymentRollbackPages(CatalogPages catalog,DeploymentRollback rollback){this.catalog=catalog;this.rollback=rollback;}
 record Ticket(Connection c,String region,String compartment,DeploymentRollback.Plan plan,Instant expires) {}
 @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var map=(Map<String,Ticket>)s.getAttribute("rollbackTickets");if(map==null){map=new LinkedHashMap<>();s.setAttribute("rollbackTickets",map);}return map;}
 @GetMapping("/deployments/rollback") String page(Authentication a,HttpServletRequest r,Model m,@RequestParam String pipeline,@RequestParam String run){
  catalog.navigation(a,r,m);var c=SecurityConfig.connection(a);var scope=catalog.selection(c,r.getSession());if(r.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
  var p=rollback.plan(c,scope.region(),scope.compartment(),pipeline,run);m.addAttribute("plan",p);m.addAttribute("scopeBack","/deployments/runs");
  synchronized(r.getSession()){var map=tickets(r.getSession());map.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(map.size()>=16)map.remove(map.keySet().iterator().next());var token=UUID.randomUUID().toString();map.put(token,new Ticket(c,scope.region(),scope.compartment(),p,Instant.now().plusSeconds(600)));m.addAttribute("ticket",token);}return "deployment-rollback";
 }
 @PostMapping("/deployments/rollback") String execute(Authentication a,HttpSession s,@RequestParam String ticket){Ticket t;synchronized(s){t=tickets(s).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(a);var scope=catalog.selection(c,s);if(!c.equals(t.c())||s.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();String run=rollback.execute(c,scope.region(),scope.compartment(),t.plan(),ticket);return "redirect:/deployments/runs?pipeline="+encode(t.plan().pipeline())+"&run="+encode(run);}
 private static String encode(String s){return java.net.URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8);}
 @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
