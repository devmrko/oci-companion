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
public class DeploymentSetupPages {
    private final CatalogPages catalog;private final DeploymentEnvironments environments;private final Registry registry;private final DeploymentSetup setup;private final VmCapacity capacity;
    public DeploymentSetupPages(CatalogPages catalog,DeploymentEnvironments environments,Registry registry,DeploymentSetup setup,VmCapacity capacity){this.catalog=catalog;this.environments=environments;this.registry=registry;this.setup=setup;this.capacity=capacity;}
    record Ticket(Connection c,String region,String compartment,Instant expires,DeploymentSetup.Preview preview) {}
    @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("deploymentSetupTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("deploymentSetupTickets",m);}return m;}
    private String issue(HttpSession s,Connection c,CatalogPages.Selection scope,DeploymentSetup.Preview preview){synchronized(s){var map=tickets(s);map.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(map.size()>=16)map.remove(map.keySet().iterator().next());String key=UUID.randomUUID().toString();map.put(key,new Ticket(c,scope.region(),scope.compartment(),Instant.now().plusSeconds(600),preview));return key;}}
    private Ticket consume(Authentication auth,HttpSession session,String key){Ticket t;synchronized(session){t=tickets(session).remove(key);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(!c.equals(t.c())||!s.region().equals(t.region())||!s.compartment().equals(t.compartment())||session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();return t;}
    private void base(Authentication auth,HttpServletRequest request,Model model){catalog.navigation(auth,request,model);model.addAttribute("connection",SecurityConfig.connection(auth));model.addAttribute("scopeBack","/deployments/configure");if(!request.getMethod().equals("GET"))model.addAttribute("navSelection",catalog.selection(SecurityConfig.connection(auth),request.getSession()));}
    private static DeploymentSetup.Input initial(String environment,String repository){return new DeploymentSetup.Input(environment,repository,"","","","/var/ocarun_staging/application","ocarun","","","","","","","",600,1);}
    private void choices(Authentication auth,HttpServletRequest request,Model model,DeploymentSetup.Input input,String page,String instance){
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,request.getSession());model.addAttribute("input",input);model.addAttribute("instanceId",instance);
        if(request.getSession().getAttribute("scopeInvalid")!=null)return;
        model.addAttribute("formTicket",issue(request.getSession(),c,s,null));
        try{model.addAttribute("environments",environments.environments(c,s.region(),s.compartment()).stream().filter(e->e.type().equals("VM")&&e.state().equals("ACTIVE")).toList());}catch(BusinessException e){model.addAttribute("environmentError",e.getMessage());}
        try{model.addAttribute("repositories",registry.list(c,s.region(),s.compartment(),"GENERIC").stream().filter(r->Boolean.TRUE.equals(r.immutable())).toList());}catch(BusinessException e){model.addAttribute("repositoryError",e.getMessage());}
        if(!input.repository().isBlank())try{var artifacts=registry.artifacts(c,s.region(),s.compartment(),"GENERIC",input.repository(),page);model.addAttribute("artifacts",artifacts);model.addAttribute("selectedArtifactMissing",model.getAttribute("preview")!=null&&artifacts.items().stream().noneMatch(a->a.id().equals(input.artifact())));}catch(BusinessException e){model.addAttribute("repositoryError",e.getMessage());}
        if(!input.environment().isBlank())try{
            var env=environments.environment(c,s.region(),s.compartment(),input.environment());if(!env.type().equals("VM"))throw Errors.SCOPE.exception();model.addAttribute("environment",env);
            String id=instance.isBlank()&&!env.instances().isEmpty()?env.instances().getFirst():instance;model.addAttribute("instanceId",id);
            if(!id.isBlank()){if(!env.instances().contains(id))throw Errors.SCOPE.exception();model.addAttribute("capacity",capacity.read(c,s.region(),s.compartment(),id));}
        }catch(BusinessException e){model.addAttribute("environmentError",e.getMessage());}
    }
    @GetMapping("/deployments/configure") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String environment,@RequestParam(defaultValue="") String repository,@RequestParam(defaultValue="") String page,@RequestParam(defaultValue="") String instance){base(auth,request,model);choices(auth,request,model,initial(environment,repository),page,instance);return "deployment-setup";}
    @PostMapping("/deployments/configure/preview") String preview(Authentication auth,HttpServletRequest request,Model model,@RequestParam String ticket,@ModelAttribute("input") DeploymentSetup.Input input){
        var t=consume(auth,request.getSession(),ticket);if(t.preview()!=null)throw Errors.BUILD_CHANGED.exception();base(auth,request,model);
        try{var p=setup.preview(t.c(),t.region(),t.compartment(),input);model.addAttribute("preview",p);model.addAttribute("createTicket",issue(request.getSession(),t.c(),catalog.selection(t.c(),request.getSession()),p));}
        catch(BusinessException e){model.addAttribute("setupError",e.getMessage());}
        choices(auth,request,model,input,"","");return "deployment-setup";
    }
    @PostMapping("/deployments/configure/create") String create(Authentication auth,HttpServletRequest request,Model model,@RequestParam String ticket){
        var t=consume(auth,request.getSession(),ticket);if(t.preview()==null)throw Errors.BUILD_CHANGED.exception();base(auth,request,model);
        model.addAttribute("input",t.preview().input());
        try{model.addAttribute("created",setup.create(t.c(),t.region(),t.compartment(),t.preview(),ticket));}catch(BusinessException e){model.addAttribute("setupError",e.getMessage());}
        return "deployment-setup";
    }
    @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
