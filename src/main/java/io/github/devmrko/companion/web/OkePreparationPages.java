package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @Profile("!legacy")
public class OkePreparationPages {
 private final CatalogPages catalog;private final OkePreparation preparation;private final BuildAdministration projects;
 public OkePreparationPages(CatalogPages catalog,OkePreparation preparation,BuildAdministration projects){this.catalog=catalog;this.preparation=preparation;this.projects=projects;}
 record Ticket(Connection connection,String region,String compartment,String cluster,Instant expires) {}
 @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("okePreparationTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("okePreparationTickets",m);}return m;}
 @GetMapping("/environments/oke") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String cluster){
  var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);model.addAttribute("connection",c);model.addAttribute("scopeBack","/environments/oke");model.addAttribute("selectedCluster",cluster);
  if(request.getSession().getAttribute("scopeInvalid")!=null)return "oke-preparation";var scope=catalog.selection(c,request.getSession());
  try{model.addAttribute("clusters",preparation.clusters(c,scope.region(),scope.compartment()));if(!cluster.isBlank()){
   model.addAttribute("detail",preparation.read(c,scope.region(),scope.compartment(),cluster));model.addAttribute("projects",projects.projects(c,scope.region(),scope.compartment()));
   synchronized(request.getSession()){var m=tickets(request.getSession());m.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(m.size()>=16)m.remove(m.keySet().iterator().next());var token=UUID.randomUUID().toString();m.put(token,new Ticket(c,scope.region(),scope.compartment(),cluster,Instant.now().plusSeconds(600)));model.addAttribute("ticket",token);}
  }}catch(BusinessException e){model.addAttribute("message",e.getMessage());}return "oke-preparation";
 }
 @PostMapping("/environments/oke") String create(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam String project,@RequestParam String name,@RequestParam(defaultValue="") String description,@RequestParam(defaultValue="") String subnet,@RequestParam(required=false) List<String> nsgs){
  Ticket t;synchronized(session){t=tickets(session).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);
  if(!c.equals(t.connection())||session.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();
  try{var id=preparation.create(c,t.region(),t.compartment(),t.cluster(),project,name,description,subnet,nsgs==null?List.of():nsgs,ticket);flash.addFlashAttribute("message","OKE 환경 생성 요청 접수 · OCI 상태를 확인하세요.");return "redirect:/environments?environment="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);}catch(BusinessException e){flash.addFlashAttribute("message",e.getMessage());return "redirect:/environments/oke";}
 }
 @ExceptionHandler(BusinessException.class) @ResponseBody org.springframework.http.ResponseEntity<String> error(BusinessException e){return org.springframework.http.ResponseEntity.status(409).body(e.getMessage());}
}
