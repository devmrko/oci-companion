package io.github.devmrko.companion.web;
import io.github.devmrko.companion.adapter.*;
import io.github.devmrko.companion.port.OkeDeploymentSetup;
import io.github.devmrko.companion.port.DeploymentOperations;
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
public class SourceServiceOnboardingPages {
 private final CatalogPages catalog;private final ServiceOnboarding existing;private final SourceServiceOnboarding source;
 public SourceServiceOnboardingPages(CatalogPages catalog,ServiceOnboarding existing,SourceServiceOnboarding source){this.catalog=catalog;this.existing=existing;this.source=source;}
 record Ticket(DeploymentOperations.Context context,Object value,String run,String token,Instant expires){}
 private DeploymentOperations.Context context(Authentication a,HttpSession session){var c=SecurityConfig.connection(a);var selected=catalog.selection(c,session);if(c.demo()||session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();return new DeploymentOperations.Context(c,selected.region(),selected.compartment(),session.getId());}
 private DeploymentOperations.Context base(Authentication a,HttpServletRequest request,Model m){catalog.navigation(a,request,m);var c=context(a,request.getSession());m.addAttribute("sourceMode",true);m.addAttribute("scopeBack","/services/new/source");m.addAttribute("templates",existing.templates(c));return c;}
 private void ticket(HttpSession s,DeploymentOperations.Context c,Object value,String run,Model m){String token=UUID.randomUUID().toString();s.setAttribute("sourceOnboardingTicket",new Ticket(c,value,run,token,Instant.now().plusSeconds(600)));m.addAttribute("sourceTicket",token);}
 private Ticket consume(HttpSession s,DeploymentOperations.Context c,String token){Ticket t;synchronized(s){t=(Ticket)s.getAttribute("sourceOnboardingTicket");s.removeAttribute("sourceOnboardingTicket");}if(t==null||!t.context().equals(c)||!t.token().equals(token)||t.expires().isBefore(Instant.now()))throw Errors.SCOPE.exception();return t;}
 @GetMapping("/services/new/source") String page(Authentication a,HttpServletRequest request,Model m,@RequestParam(defaultValue="") String run){
  var c=base(a,request,m);
  if(run.isBlank()){m.addAttribute("sourceChoices",source.sources(c));m.addAttribute("sourceBuilders",source.builders(c));m.addAttribute("sourceRepositories",source.repositories(c));}
  else{var p=source.read(c,run);m.addAttribute("sourceProgress",p);if(!p.buildRun().isBlank()){var build=source.buildStatus(c,p);m.addAttribute("sourceBuild",build);if(p.state().equals("BUILDING")&&build.state().equals("SUCCEEDED")){var preview=source.deploymentPreview(c,p);m.addAttribute("sourceDeployment",preview);ticket(request.getSession(),c,preview,p.id(),m);}}
   if(!p.deployment().isBlank()){var progress=existing.finish(c,p.deployment());m.addAttribute("progress",progress);m.addAttribute("deployment",existing.status(c,progress));if(progress.state().equals("REGISTERED"))m.addAttribute("sourceProgress",source.registered(c,run));}
  }return "service-onboarding";
 }
 @PostMapping("/services/new/source/inspect") String inspect(Authentication a,HttpServletRequest request,Model m,@ModelAttribute SourceServiceOnboarding.Request input){var c=base(a,request,m);var draft=source.inspect(c,input);m.addAttribute("sourceDraft",draft);ticket(request.getSession(),c,draft,"",m);return "service-onboarding";}
 @PostMapping("/services/new/source/preview") String preview(Authentication a,HttpServletRequest request,Model m,@RequestParam String ticket,@RequestParam Map<String,String> values){var c=base(a,request,m);var t=consume(request.getSession(),c,ticket);if(!(t.value() instanceof SourceServiceOnboarding.Draft draft))throw Errors.SCOPE.exception();var plan=source.preview(c,draft,values);m.addAttribute("sourcePlan",plan);ticket(request.getSession(),c,plan,"",m);return "service-onboarding";}
 @PostMapping("/services/new/source/build") String build(Authentication a,HttpSession s,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){var c=context(a,s);var t=consume(s,c,ticket);if(!confirmed||!(t.value() instanceof SourceServiceOnboarding.Plan))throw Errors.SCOPE.exception();var p=source.start(c,(SourceServiceOnboarding.Plan)t.value(),t.token());return "redirect:/services/new/source?run="+p.id();}
 @PostMapping("/services/new/source/deploy") String deploy(Authentication a,HttpSession s,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){var c=context(a,s);var t=consume(s,c,ticket);if(!confirmed||!(t.value() instanceof OkeDeploymentSetup.Preview))throw Errors.SCOPE.exception();source.deploy(c,t.run(),(OkeDeploymentSetup.Preview)t.value());return "redirect:/services/new/source?run="+t.run();}
 @PostMapping("/services/new/source/resume") String resume(Authentication a,HttpSession s,@RequestParam String run,@RequestParam(defaultValue="false") boolean confirmed){if(!confirmed)throw Errors.SCOPE.exception();source.resume(context(a,s),run);return "redirect:/services/new/source?run="+run;}
 @ExceptionHandler({IllegalStateException.class,IllegalArgumentException.class,io.github.devmrko.companion.error.BusinessException.class}) String error(Exception e,Model m){m.addAttribute("message",PipelineIamRules.safe(e.getMessage()));return "operating-service-error";}
}
