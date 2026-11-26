package io.github.devmrko.companion.web;
import io.github.devmrko.companion.adapter.ServiceOnboarding;
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
@Controller @Profile("!legacy & !demo")
public class ServiceOnboardingPages {
 private final CatalogPages catalog;private final ServiceOnboarding onboarding;
 public ServiceOnboardingPages(CatalogPages catalog,ServiceOnboarding onboarding){this.catalog=catalog;this.onboarding=onboarding;}
 record Ticket(DeploymentOperations.Context context,ServiceOnboarding.Plan plan,Instant expires,String token){}
 private DeploymentOperations.Context context(Authentication a,HttpSession s){var c=SecurityConfig.connection(a);var selection=catalog.selection(c,s);if(c.demo()||s.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();return new DeploymentOperations.Context(c,selection.region(),selection.compartment(),s.getId());}
 private DeploymentOperations.Context base(Authentication a,HttpServletRequest request,Model m){catalog.navigation(a,request,m);var c=context(a,request.getSession());m.addAttribute("templates",onboarding.templates(c));m.addAttribute("scopeBack","/services/new");return c;}
 @GetMapping("/services/new") String page(Authentication a,HttpServletRequest request,Model m,@RequestParam(defaultValue="") String source,@RequestParam(defaultValue="") String template,@RequestParam(defaultValue="") String run){
  var c=base(a,request,m);if(!source.isBlank()){
   var choices=onboarding.templates(c).stream().filter(e->e.registration().source().equals(source)).toList();m.addAttribute("choices",choices);m.addAttribute("source",source);
   if(template.isBlank()&&choices.size()==1)template=choices.getFirst().registration().name();
   if(!template.isBlank()){String selected=template;var chosen=choices.stream().filter(e->e.registration().name().equals(selected)).findFirst().orElseThrow(Errors.SCOPE::exception);m.addAttribute("chosen",chosen);m.addAttribute("clusters",onboarding.clusters(c).stream().filter(x->x.id().equals(chosen.cluster())).toList());m.addAttribute("namespaces",onboarding.namespaces(c,template,chosen.cluster()));}
  }
  if(!run.isBlank()){var p=onboarding.finish(c,run);m.addAttribute("progress",p);m.addAttribute("deployment",onboarding.status(c,p));}return "service-onboarding";
 }
 @PostMapping("/services/new/preview") String preview(Authentication a,HttpServletRequest request,Model m,@ModelAttribute ServiceOnboarding.Selection selection){var c=base(a,request,m);var plan=onboarding.preview(c,selection);String token=UUID.randomUUID().toString();request.getSession().setAttribute("serviceOnboardingTicket",new Ticket(c,plan,Instant.now().plusSeconds(600),token));m.addAttribute("plan",plan);m.addAttribute("ticket",token);return "service-onboarding";}
 @PostMapping("/services/new/create") String create(Authentication a,HttpSession s,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){var c=context(a,s);Ticket t;synchronized(s){t=(Ticket)s.getAttribute("serviceOnboardingTicket");s.removeAttribute("serviceOnboardingTicket");}if(!confirmed||t==null||!t.token().equals(ticket)||!t.context().equals(c)||t.expires().isBefore(Instant.now()))throw Errors.SCOPE.exception();var p=onboarding.create(c,t.plan(),t.token());return "redirect:/services/new?run="+p.id();}
 @PostMapping("/services/new/resume") String resume(Authentication a,HttpSession session,@RequestParam String run,@RequestParam(defaultValue="false") boolean confirmed){if(!confirmed)throw Errors.INVALID.exception();var p=onboarding.resume(context(a,session),run);return "redirect:/services/new?run="+p.id();}
 record RetirementTicket(DeploymentOperations.Context context,ServiceOnboarding.RetirementPlan plan,Instant expires,String token){}
 @PostMapping("/services/new/retirement-preview") String retirementPreview(Authentication a,HttpServletRequest request,Model m,@RequestParam String run){
  var c=base(a,request,m);var plan=onboarding.retirementPreview(c,run);String token=UUID.randomUUID().toString();request.getSession().setAttribute("retirementTicket",new RetirementTicket(c,plan,Instant.now().plusSeconds(600),token));m.addAttribute("retirement",plan);m.addAttribute("retirementProgress",onboarding.read(c,run));m.addAttribute("retirementToken",token);return "service-onboarding";
 }
 @PostMapping("/services/new/retire") String retire(Authentication a,HttpSession session,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){
  var c=context(a,session);RetirementTicket t;synchronized(session){t=(RetirementTicket)session.getAttribute("retirementTicket");session.removeAttribute("retirementTicket");}
  if(!confirmed||t==null||!t.token().equals(ticket)||!t.context().equals(c)||t.expires().isBefore(Instant.now()))throw Errors.SCOPE.exception();onboarding.retire(c,t.plan());return "redirect:/services/new?run="+t.plan().run();
 }
 @ExceptionHandler({IllegalStateException.class,IllegalArgumentException.class,io.github.devmrko.companion.error.BusinessException.class}) String error(Exception e,Model m){if(e instanceof ExistingDeploymentName existing)m.addAttribute("redeployHref",existing.href());m.addAttribute("message",PipelineIamRules.safe(e.getMessage()));return "operating-service-error";}
}
