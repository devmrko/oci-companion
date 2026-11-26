package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
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
public class ArmBuildPages {
 private final CatalogPages catalog;private final CatalogService sources;private final Registry registry;private final ArmBuilds builds;private final DeploymentEnvironments environments;private final FunctionBuilds functions;
 public ArmBuildPages(CatalogPages catalog,CatalogService sources,Registry registry,ArmBuilds builds,DeploymentEnvironments environments,FunctionBuilds functions){this.functions=functions;this.environments=environments;this.catalog=catalog;this.sources=sources;this.registry=registry;this.builds=builds;}
 record Ticket(Connection actor,String region,String compartment,ArmBuilds.Plan plan,Instant expires){}
 @SuppressWarnings("unchecked") Map<String,Ticket> tickets(HttpSession s){var map=(Map<String,Ticket>)s.getAttribute("armBuildTickets");if(map==null){map=new LinkedHashMap<>();s.setAttribute("armBuildTickets",map);}return map;}
 private CatalogPages.Selection scope(Authentication a,HttpSession session){var c=SecurityConfig.connection(a);var s=catalog.selection(c,session);if(c.demo()||session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();return s;}
 private void base(Authentication a,HttpServletRequest r,Model m){catalog.navigation(a,r,m);var s=scope(a,r.getSession());var c=SecurityConfig.connection(a);m.addAttribute("scopeBack","/builds/arm");m.addAttribute("environments",environments.environments(c,s.region(),s.compartment()).stream().filter(e->e.type().equals("OKE")&&e.state().equals("ACTIVE")).toList());m.addAttribute("selection",s);m.addAttribute("builders",builds.builders(c,s.region(),s.compartment()));m.addAttribute("sources",sources.sources(c,s.region(),s.compartment()).stream().filter(x->x.provider().equals("OCI")).toList());m.addAttribute("repositories",registry.list(c,s.region(),s.compartment(),"CONTAINER"));var functionBuilders=new ArrayList<FunctionBuilds.Builder>();try{for(var app:functions.applications(c,s.region(),s.compartment()))if(app.state().equals("ACTIVE"))functionBuilders.addAll(functions.builders(c,s.region(),s.compartment(),app.id()).stream().filter(f->f.contract().equals("sustena-layer-v1")&&f.state().equals("ACTIVE")).toList());}catch(Exception error){m.addAttribute("functionLookupError","Function 목록을 조회하지 못했습니다. Functions 빌드 환경에서 연결·권한을 확인하세요.");}m.addAttribute("functionImageBuilders",functionBuilders);m.addAttribute("pendingBuilds",builds.pending(c,s.region(),s.compartment()));m.addAttribute("managedImages",builds.managedImages());m.addAttribute("armManagedAvailable",builds.managedImages().stream().anyMatch(x->x.contains("ARM")||x.contains("AARCH64")));}
 @GetMapping("/builds/arm") String page(Authentication a,HttpServletRequest r,Model m,@RequestParam(defaultValue="") String run){base(a,r,m);if(!run.isBlank()){var s=scope(a,r.getSession());m.addAttribute("run",builds.read(SecurityConfig.connection(a),s.region(),s.compartment(),run));}return "arm-build";}
 @PostMapping("/builds/arm/builders") String builder(Authentication a,HttpSession session,@RequestParam String region,@RequestParam String compartment,@ModelAttribute ArmBuilds.Builder builder){var s=scope(a,session);if(!s.region().equals(region)||!s.compartment().equals(compartment))throw Errors.SCOPE.exception();builds.saveBuilder(SecurityConfig.connection(a),region,compartment,builder);return "redirect:/builds/arm";}
 @PostMapping("/builds/arm/builders/remove") String removeBuilder(Authentication a,HttpSession session,@RequestParam String name,@RequestParam(defaultValue="false") boolean confirmed){if(!confirmed)throw Errors.INVALID.exception();var s=scope(a,session);builds.removeBuilder(SecurityConfig.connection(a),s.region(),s.compartment(),name);return "redirect:/builds/arm";}
 @PostMapping("/builds/arm/preview") String preview(Authentication a,HttpServletRequest r,Model m,@RequestParam String region,@RequestParam String compartment,@ModelAttribute ArmBuilds.Input input){
  var s=scope(a,r.getSession());if(!s.region().equals(region)||!s.compartment().equals(compartment))throw Errors.SCOPE.exception();var c=SecurityConfig.connection(a);var plan=builds.preview(c,region,compartment,input);base(a,r,m);m.addAttribute("plan",plan);String key=UUID.randomUUID().toString();synchronized(r.getSession()){var map=tickets(r.getSession());if(map.size()>=16)map.remove(map.keySet().iterator().next());map.put(key,new Ticket(c,region,compartment,plan,Instant.now().plusSeconds(600)));}m.addAttribute("ticket",key);return "arm-build";
 }
 @PostMapping("/builds/arm/run") String start(Authentication a,HttpSession session,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed,@RequestParam(defaultValue="") String username,@RequestParam(defaultValue="") String password){
  Ticket t;synchronized(session){t=tickets(session).remove(ticket);}var s=scope(a,session);var c=SecurityConfig.connection(a);if(!confirmed||t==null||t.expires().isBefore(Instant.now())||!c.equals(t.actor())||!s.region().equals(t.region())||!s.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();char[] secret=password.toCharArray();try{return "redirect:/builds/arm?run="+builds.start(c,t.region(),t.compartment(),t.plan(),username,secret);}finally{Arrays.fill(secret,'\0');}
 }
 @PostMapping("/builds/arm/reconcile") String reconcile(Authentication a,HttpSession session,@RequestParam String run){var s=scope(a,session);builds.reconcile(SecurityConfig.connection(a),s.region(),s.compartment(),run);return "redirect:/builds/arm?run="+run;}
 @PostMapping("/builds/arm/abandon") String abandon(Authentication a,HttpSession session,@RequestParam String run,@RequestParam(defaultValue="false") boolean confirmed){if(!confirmed)throw Errors.SCOPE.exception();var s=scope(a,session);builds.abandon(SecurityConfig.connection(a),s.region(),s.compartment(),run);return "redirect:/builds/arm?run="+run;}
 @ExceptionHandler(IllegalStateException.class) String error(IllegalStateException e,RedirectAttributes flash){flash.addFlashAttribute("message",PipelineIamRules.safe(e.getMessage()));return "redirect:/builds/arm";}
}
