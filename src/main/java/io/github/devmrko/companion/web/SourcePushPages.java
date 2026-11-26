package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Source;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @Profile("!legacy")
public class SourcePushPages {
 private final CatalogPages catalog;private final CatalogService sources;private final SourcePush push;
 public SourcePushPages(CatalogPages catalog,CatalogService sources,SourcePush push){this.catalog=catalog;this.sources=sources;this.push=push;}
 record Ticket(Connection actor,String region,String compartment,SourcePush.Plan plan,Instant expires){}
 @SuppressWarnings("unchecked") Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("sourcePushTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("sourcePushTickets",m);}return m;}
 private CatalogPages.Selection scope(Authentication a,HttpSession s){var scope=catalog.selection(SecurityConfig.connection(a),s);if(s.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();return scope;}
 private Source source(Connection c,CatalogPages.Selection s,String id){return sources.sources(c,s.region(),s.compartment()).stream().filter(x->x.id().equals(id)).findFirst().orElseThrow(Errors.SCOPE::exception);}
 private void base(Authentication a,HttpServletRequest r,Model m){catalog.navigation(a,r,m);var s=scope(a,r.getSession());m.addAttribute("selection",s);m.addAttribute("sources",sources.sources(SecurityConfig.connection(a),s.region(),s.compartment()));}
 @GetMapping("/sources/push") String page(Authentication a,HttpServletRequest r,Model m){base(a,r,m);return "source-push";}
 @PostMapping("/sources/push/preview") String preview(Authentication a,HttpServletRequest r,Model m,@RequestParam String source,@RequestParam String destination,@RequestParam String branch,@RequestParam String targetBranch,@RequestParam String username,@RequestParam String secret,@RequestParam String targetUsername,@RequestParam String targetSecret,@RequestParam String region,@RequestParam String compartment){
  var s=scope(a,r.getSession());var c=SecurityConfig.connection(a);if(!s.region().equals(region)||!s.compartment().equals(compartment))throw Errors.SCOPE.exception();
  var plan=push.preview(c,s.region(),new GitSpecifications.Target(source(c,s,source),branch,"build_spec.yaml",username,secret),new GitSpecifications.Target(source(c,s,destination),targetBranch,"build_spec.yaml",targetUsername,targetSecret));
  base(a,r,m);String id=UUID.randomUUID().toString();synchronized(r.getSession()){var map=tickets(r.getSession());if(map.size()>=8)map.remove(map.keySet().iterator().next());map.put(id,new Ticket(c,s.region(),s.compartment(),plan,Instant.now().plusSeconds(600)));}m.addAttribute("ticket",id);m.addAttribute("plan",plan);return "source-push";
 }
 @PostMapping("/sources/push/run") String run(Authentication a,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){
  Ticket t;synchronized(session){t=tickets(session).remove(ticket);}var c=SecurityConfig.connection(a);var s=scope(a,session);
  if(!confirmed||t==null||t.expires().isBefore(Instant.now())||!t.actor().equals(c)||!t.region().equals(s.region())||!t.compartment().equals(s.compartment()))throw Errors.SCOPE.exception();
  if(!source(c,s,t.plan().source().source().id()).equals(t.plan().source().source())||!source(c,s,t.plan().destination().source().id()).equals(t.plan().destination().source()))throw Errors.GIT_CONFLICT.exception();
  String revision=push.push(c,s.region(),t.plan());flash.addFlashAttribute("message","새 브랜치 push 완료 · "+revision+" · 빌드 구성에서 이 브랜치를 선택하세요.");return "redirect:/sources/push";
 }
 @ExceptionHandler(io.github.devmrko.companion.error.BusinessException.class) String error(io.github.devmrko.companion.error.BusinessException e,RedirectAttributes flash){flash.addFlashAttribute("message",e.code().equals("GIT_CONFLICT")?"대상 브랜치가 이미 있거나 소스가 변경되었습니다. 초기 커밋은 그대로 두고 새로운 브랜치 이름으로 다시 미리보기 하세요.":e.getMessage());return "redirect:/sources/push";}
}
