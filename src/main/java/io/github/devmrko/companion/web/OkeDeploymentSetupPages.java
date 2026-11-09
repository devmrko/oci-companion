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
@Controller @Profile("!legacy")
public class OkeDeploymentSetupPages {
 private final CatalogPages catalog;private final DeploymentEnvironments environments;private final Registry registry;private final OkeDeploymentSetup setup;
 public OkeDeploymentSetupPages(CatalogPages catalog,DeploymentEnvironments environments,Registry registry,OkeDeploymentSetup setup){this.catalog=catalog;this.environments=environments;this.registry=registry;this.setup=setup;}
 record Ticket(Connection c,String region,String compartment,Instant expires,OkeDeploymentSetup.Preview preview) {}
 @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("okeSetupTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("okeSetupTickets",m);}return m;}
 private String issue(HttpSession s,Connection c,CatalogPages.Selection scope,OkeDeploymentSetup.Preview preview){synchronized(s){var m=tickets(s);m.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(m.size()>=16)m.remove(m.keySet().iterator().next());String key=UUID.randomUUID().toString();m.put(key,new Ticket(c,scope.region(),scope.compartment(),Instant.now().plusSeconds(600),preview));return key;}}
 private Ticket checked(Authentication auth,HttpSession s,String key,boolean consume){Ticket t;synchronized(s){t=consume?tickets(s).remove(key):tickets(s).get(key);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,s);if(!c.equals(t.c())||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment())||s.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();return t;}
 private void base(Authentication auth,HttpServletRequest request,Model m){catalog.navigation(auth,request,m);m.addAttribute("connection",SecurityConfig.connection(auth));m.addAttribute("navSelection",catalog.selection(SecurityConfig.connection(auth),request.getSession()));m.addAttribute("scopeBack","/deployments/oke/configure");}
 private static OkeDeploymentSetup.Input initial(String environment,String repository){return new OkeDeploymentSetup.Input(environment,repository,"","","","","","amd64",1,250,256,8080,"","","","","",true);}
 private void choices(Authentication auth,HttpServletRequest request,Model m,OkeDeploymentSetup.Input i,String page){var c=SecurityConfig.connection(auth);var s=catalog.selection(c,request.getSession());m.addAttribute("input",i);if(request.getSession().getAttribute("scopeInvalid")!=null)return;
  m.addAttribute("formTicket",issue(request.getSession(),c,s,null));
  try{m.addAttribute("environments",environments.environments(c,s.region(),s.compartment()).stream().filter(e->e.type().equals("OKE")&&e.state().equals("ACTIVE")).toList());m.addAttribute("repositories",registry.list(c,s.region(),s.compartment(),"CONTAINER"));if(!i.repository().isBlank())m.addAttribute("images",registry.artifacts(c,s.region(),s.compartment(),"CONTAINER",i.repository(),page));}catch(BusinessException e){m.addAttribute("setupError",e.getMessage());}
 }
 @GetMapping("/deployments/oke/configure") String page(Authentication a,HttpServletRequest r,Model m,@RequestParam(defaultValue="") String environment,@RequestParam(defaultValue="") String repository,@RequestParam(defaultValue="") String page){base(a,r,m);choices(a,r,m,initial(environment,repository),page);return "oke-deployment-setup";}
 @PostMapping("/deployments/oke/configure/preview") String preview(Authentication a,HttpServletRequest r,Model m,@RequestParam String ticket,@ModelAttribute("input") OkeDeploymentSetup.Input i){var t=checked(a,r.getSession(),ticket,true);if(t.preview()!=null)throw Errors.BUILD_CHANGED.exception();base(a,r,m);try{var p=setup.preview(t.c(),t.region(),t.compartment(),i);m.addAttribute("preview",p);m.addAttribute("createTicket",issue(r.getSession(),t.c(),catalog.selection(t.c(),r.getSession()),p));}catch(BusinessException e){m.addAttribute("setupError",e.getMessage());}choices(a,r,m,i,"");return "oke-deployment-setup";}
 @GetMapping("/deployments/oke/configure/manifest.yaml") @ResponseBody org.springframework.http.ResponseEntity<String> download(Authentication a,HttpSession s,@RequestParam String ticket){var t=checked(a,s,ticket,false);if(t.preview()==null)throw Errors.INVALID.exception();return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=deployment.yaml").contentType(org.springframework.http.MediaType.parseMediaType("application/yaml")).body(t.preview().yaml());}
 @PostMapping("/deployments/oke/configure/create") String create(Authentication a,HttpServletRequest r,Model m,@RequestParam String ticket){var t=checked(a,r.getSession(),ticket,true);if(t.preview()==null)throw Errors.BUILD_CHANGED.exception();base(a,r,m);m.addAttribute("input",t.preview().input());try{m.addAttribute("created",setup.create(t.c(),t.region(),t.compartment(),t.preview(),ticket));}catch(BusinessException e){m.addAttribute("setupError",e.getMessage());}return "oke-deployment-setup";}
 @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
