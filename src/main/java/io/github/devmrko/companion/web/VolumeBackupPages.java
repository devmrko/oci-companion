package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.VolumeBackups;
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
public class VolumeBackupPages {
 private final CatalogPages catalog;private final VolumeBackups backups;
 public VolumeBackupPages(CatalogPages catalog,VolumeBackups backups){this.catalog=catalog;this.backups=backups;}
 record Ticket(Connection c,String region,String compartment,VolumeBackups.Plan plan,Instant expires) {}
 @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("volumeBackupTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("volumeBackupTickets",m);}return m;}
 @GetMapping("/backups") String page(Authentication a,HttpServletRequest r,Model m){
  catalog.navigation(a,r,m);var c=SecurityConfig.connection(a);var scope=catalog.selection(c,r.getSession());if(r.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();m.addAttribute("connection",c);m.addAttribute("navSelection",scope);m.addAttribute("scopeBack","/backups");m.addAttribute("inventory",backups.list(c,scope.region(),scope.compartment()));return "volume-backups";
 }
 @PostMapping("/backups/preview") String preview(Authentication a,HttpServletRequest r,Model m,@RequestParam String operation,@RequestParam String target,@RequestParam String name,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String domain){
  page(a,r,m);var c=SecurityConfig.connection(a);var scope=catalog.selection(c,r.getSession());var p=backups.plan(c,scope.region(),scope.compartment(),new VolumeBackups.Input(operation,target,name,type,domain));
  synchronized(r.getSession()){var map=tickets(r.getSession());map.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(map.size()>=16)map.remove(map.keySet().iterator().next());String token=UUID.randomUUID().toString();map.put(token,new Ticket(c,scope.region(),scope.compartment(),p,Instant.now().plusSeconds(600)));m.addAttribute("ticket",token);}m.addAttribute("plan",p);return "volume-backups";
 }
 @PostMapping("/backups/apply") String apply(Authentication a,HttpSession session,RedirectAttributes flash,@RequestParam String ticket){
  Ticket t;synchronized(session){t=tickets(session).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(a);var scope=catalog.selection(c,session);if(!c.equals(t.c())||session.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();
  String id=backups.apply(c,t.region(),t.compartment(),t.plan(),ticket);flash.addFlashAttribute("message","OCI 요청 접수 · 목록을 새로 조회해 AVAILABLE 상태를 확인하세요.");flash.addFlashAttribute("createdId",id);return "redirect:/backups";
 }
 @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
