package io.github.devmrko.companion.web;
import io.github.devmrko.companion.adapter.OkeApiAccess;
import io.github.devmrko.companion.port.OkeWorkloadConnections;
import io.github.devmrko.companion.service.Errors;
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
public class OkeApiAccessPages {
 private final CatalogPages catalog;private final OkeWorkloadConnections connections;private final OkeApiAccess access;
 public OkeApiAccessPages(CatalogPages catalog,OkeWorkloadConnections connections,OkeApiAccess access){this.catalog=catalog;this.connections=connections;this.access=access;}
 record Ticket(io.github.devmrko.companion.domain.Models.Connection actor,String region,String compartment,String cluster,Instant expires){}
 @SuppressWarnings("unchecked") Map<String,Ticket> tickets(HttpSession s){var map=(Map<String,Ticket>)s.getAttribute("okeAccessTickets");if(map==null){map=new LinkedHashMap<>();s.setAttribute("okeAccessTickets",map);}return map;}
 @GetMapping("/environments/oke/access") String page(Authentication a,HttpServletRequest r,Model m,@RequestParam(defaultValue="") String cluster){
  var c=SecurityConfig.connection(a);catalog.navigation(a,r,m);var scope=catalog.selection(c,r.getSession());m.addAttribute("scopeBack","/environments/oke/access");m.addAttribute("cluster",cluster);
  if(r.getSession().getAttribute("scopeInvalid")!=null||c.demo())throw Errors.SCOPE.exception();
  var clusters=connections.clusters(c,scope.region(),scope.compartment());m.addAttribute("clusters",clusters);
  if(!cluster.isEmpty()){
   if(clusters.stream().noneMatch(x->x.id().equals(cluster)))throw Errors.SCOPE.exception();
   m.addAttribute("settings",access.read(c,scope.region(),scope.compartment(),cluster));String ticket=UUID.randomUUID().toString();
   synchronized(r.getSession()){var map=tickets(r.getSession());if(map.size()>=16)map.remove(map.keySet().iterator().next());map.put(ticket,new Ticket(c,scope.region(),scope.compartment(),cluster,Instant.now().plusSeconds(600)));}m.addAttribute("ticket",ticket);
  }return "oke-api-access";
 }
 @PostMapping("/environments/oke/access") String save(Authentication a,HttpSession s,RedirectAttributes flash,@RequestParam String ticket,@RequestParam(defaultValue="save") String action,@ModelAttribute OkeApiAccess.Settings settings){
  Ticket t;synchronized(s){t=tickets(s).remove(ticket);}var c=SecurityConfig.connection(a);var scope=catalog.selection(c,s);
  if(t==null||t.expires().isBefore(Instant.now())||!t.actor().equals(c)||!t.region().equals(scope.region())||!t.compartment().equals(scope.compartment())||s.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
  if(action.equals("disconnect"))access.disconnect(c,t.region(),t.compartment(),t.cluster());else if(action.equals("save"))access.save(c,t.region(),t.compartment(),t.cluster(),settings);else throw Errors.INVALID.exception();
  flash.addFlashAttribute("message",action.equals("disconnect")?"터널을 닫았습니다. 다음 조회에서 필요하면 다시 연결합니다.":"접속 설정을 저장했습니다. 조회 연결과 배포 참조 확인에 함께 적용합니다.");
  return "redirect:/environments/oke/access?cluster="+java.net.URLEncoder.encode(t.cluster(),java.nio.charset.StandardCharsets.UTF_8);
 }
}
