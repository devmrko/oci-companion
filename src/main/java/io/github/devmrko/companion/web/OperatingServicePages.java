package io.github.devmrko.companion.web;

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
public class OperatingServicePages {
 private final CatalogPages catalog;private final CatalogService sources;private final OperatingServices services;private final DeploymentEnvironments environments;private final DeploymentOperations deployments;private final OkeDeploymentInputs inputs;private final ArmBuilds builds;private final NodePortAccess access;
 public OperatingServicePages(CatalogPages catalog,CatalogService sources,OperatingServices services,DeploymentEnvironments environments,DeploymentOperations deployments,OkeDeploymentInputs inputs,ArmBuilds builds,NodePortAccess access){this.catalog=catalog;this.sources=sources;this.services=services;this.environments=environments;this.deployments=deployments;this.inputs=inputs;this.builds=builds;this.access=access;}
 record Ticket(DeploymentOperations.Context context,Object plan,String action,Instant expires){}
 private DeploymentOperations.Context context(Authentication a,HttpSession session){var c=SecurityConfig.connection(a);var s=catalog.selection(c,session);if(c.demo()||session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();return new DeploymentOperations.Context(c,s.region(),s.compartment(),session.getId());}
 @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var map=(Map<String,Ticket>)s.getAttribute("operatingServiceTickets");if(map==null){map=new LinkedHashMap<>();s.setAttribute("operatingServiceTickets",map);}return map;}
 private String issue(HttpSession s,DeploymentOperations.Context c,Object plan,String action){synchronized(s){var map=tickets(s);if(map.size()>=16)map.remove(map.keySet().iterator().next());String id=UUID.randomUUID().toString();map.put(id,new Ticket(c,plan,action,Instant.now().plusSeconds(600)));return id;}}
 private Ticket consume(HttpSession s,DeploymentOperations.Context c,String id,String action,boolean confirmed){synchronized(s){var t=tickets(s).remove(id);if(!confirmed||t==null||!t.context().equals(c)||!t.action().equals(action)||t.expires().isBefore(Instant.now()))throw Errors.SCOPE.exception();return t;}}
 private void base(Authentication a,HttpServletRequest request,Model m){catalog.navigation(a,request,m);var c=context(a,request.getSession());m.addAttribute("entries",services.list(c));m.addAttribute("environments",environments.environments(c.connection(),c.region(),c.compartment()).stream().filter(e->e.type().equals("OKE")&&e.state().equals("ACTIVE")).toList());m.addAttribute("scopeBack","/services");}
 @GetMapping("/services") String page(Authentication a,HttpServletRequest request,Model m,@RequestParam(defaultValue="") String environment,@RequestParam(defaultValue="") String selected,@RequestParam(defaultValue="") String run){
  base(a,request,m);var c=context(a,request.getSession());
  if(!environment.isBlank()){
   var e=environments.environment(c.connection(),c.region(),c.compartment(),environment);if(!e.type().equals("OKE"))throw Errors.SCOPE.exception();
   m.addAttribute("environment",environment);m.addAttribute("configuration",inputs.read(c.connection(),c.region(),c.compartment(),environment).orElseThrow(Errors.DEPLOYMENT_INPUT::exception));
   m.addAttribute("sources",sources.sources(c.connection(),c.region(),c.compartment()).stream().filter(s->s.provider().equals("OCI")).toList());m.addAttribute("builders",builds.builders(c.connection(),c.region(),c.compartment()));m.addAttribute("pipelines",deployments.pipelines(c));m.addAttribute("securityLists",access.lists(c.connection(),c.region(),c.compartment(),e.clusterId()));
  }
  if(!selected.isBlank()){var entry=services.get(c,selected);m.addAttribute("selectedService",entry);m.addAttribute("commits",services.commits(c,entry));m.addAttribute("recent",services.recent(c,selected));}
  if(!run.isBlank()){var status=services.status(c,run);m.addAttribute("status",status);m.addAttribute("selectedService",status.release().service());}
  return "operating-services";
 }
 @PostMapping("/services/register") String register(Authentication a,HttpSession session,@ModelAttribute OperatingServices.Registration registration){var e=services.register(context(a,session),registration);return "redirect:/services?selected="+e.registration().name();}
 @PostMapping("/services/configuration") String configuration(Authentication a,HttpSession session,@RequestParam String selected){var i=services.restoreConfiguration(context(a,session),selected);return "redirect:"+org.springframework.web.util.UriComponentsBuilder.fromPath("/deployments/oke/configure").queryParam("environment",i.environment()).queryParam("repository",i.repository()).build().encode().toUriString();}
 @PostMapping("/services/remove") String remove(Authentication a,HttpSession session,@RequestParam String selected,@RequestParam(defaultValue="false") boolean confirmed){if(!confirmed)throw Errors.INVALID.exception();services.remove(context(a,session),selected);return "redirect:/services";}
 @PostMapping("/services/build/preview") String buildPreview(Authentication a,HttpServletRequest request,Model m,@RequestParam String selected,@RequestParam String commit){base(a,request,m);var c=context(a,request.getSession());var plan=services.prepareBuild(c,selected,commit);m.addAttribute("buildPlan",plan);m.addAttribute("ticket",issue(request.getSession(),c,plan,"build"));return "operating-services";}
 @PostMapping("/services/build") String build(Authentication a,HttpSession session,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){var c=context(a,session);var t=consume(session,c,ticket,"build",confirmed);var run=services.build(c,(OperatingServices.BuildPlan)t.plan());return "redirect:/services?run="+run.id();}
 @PostMapping("/services/deploy/preview") String deployPreview(Authentication a,HttpServletRequest request,Model m,@RequestParam String run){base(a,request,m);var c=context(a,request.getSession());var plan=services.prepareDeploy(c,run);m.addAttribute("deployPlan",plan);m.addAttribute("ticket",issue(request.getSession(),c,plan,"deploy"));return "operating-services";}
 @PostMapping("/services/deploy") String deploy(Authentication a,HttpSession session,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){var c=context(a,session);var t=consume(session,c,ticket,"deploy",confirmed);var run=services.deploy(c,(OperatingServices.DeployPlan)t.plan());return "redirect:/services?run="+run.id();}
 @ExceptionHandler({IllegalStateException.class,IllegalArgumentException.class,io.github.devmrko.companion.error.BusinessException.class}) String error(Exception e,Model model){model.addAttribute("message",PipelineIamRules.safe(e.getMessage()));return "operating-service-error";}
}
