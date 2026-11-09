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
public class DeploymentEnvironmentPages {
    private final CatalogPages catalog;private final DeploymentEnvironments environments;private final BuildAdministration projects;
    public DeploymentEnvironmentPages(CatalogPages catalog,DeploymentEnvironments environments,BuildAdministration projects){this.catalog=catalog;this.environments=environments;this.projects=projects;}
    record Ticket(Connection connection,String region,String compartment,Instant expires) {}
    @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("deploymentEnvironmentTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("deploymentEnvironmentTickets",m);}return m;}
    @GetMapping({"/environments","/deployments"}) String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="existing") String tab,@RequestParam(defaultValue="") String environment,@RequestParam(defaultValue="") String instance,@RequestParam(defaultValue="0") int page){
        if(!Set.of("existing","new","vms").contains(tab))throw Errors.INVALID.exception();
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var scope=catalog.selection(c,request.getSession());
        boolean deploying=request.getRequestURI().equals("/deployments");model.addAttribute("connection",c);model.addAttribute("deploying",deploying);model.addAttribute("tab",tab);model.addAttribute("scopeBack",deploying?"/deployments":"/environments");model.addAttribute("selectedInstance",instance);model.addAttribute("environmentId",environment);model.addAttribute("instanceNames",Map.of());
        if(request.getSession().getAttribute("scopeInvalid")!=null)return "deployment-environments";
        if(tab.equals("existing"))try{var all=environments.environments(c,scope.region(),scope.compartment());int last=Math.max(0,(all.size()-1)/20);int current=Math.max(0,Math.min(page,last));model.addAttribute("environments",all.subList(current*20,Math.min(all.size(),current*20+20)));model.addAttribute("page",current);model.addAttribute("last",last);model.addAttribute("total",all.size());}catch(BusinessException e){model.addAttribute("environmentError",e.getMessage());}
        if(!environment.isBlank())try{model.addAttribute("selectedEnvironment",environments.environment(c,scope.region(),scope.compartment(),environment));}catch(BusinessException e){model.addAttribute("environmentError",e.getMessage());}
        if(tab.equals("new")||tab.equals("vms")||(model.getAttribute("selectedEnvironment") instanceof DeploymentEnvironments.Environment selected && selected.type().equals("VM")))try{var vms=environments.instances(c,scope.region(),scope.compartment());model.addAttribute("instances",vms);var names=new HashMap<String,String>();for(var vm:vms)names.put(vm.id(),vm.name());model.addAttribute("instanceNames",names);}catch(BusinessException e){model.addAttribute("instanceError",e.getMessage());}
        if(!instance.isBlank())try{model.addAttribute("inspection",environments.inspect(c,scope.region(),scope.compartment(),instance));}catch(BusinessException e){model.addAttribute("instanceError",e.getMessage());}
        if(tab.equals("new"))try{
            model.addAttribute("projects",projects.projects(c,scope.region(),scope.compartment()));
            synchronized(request.getSession()){var m=tickets(request.getSession());m.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(m.size()>=32)m.remove(m.keySet().iterator().next());var token=UUID.randomUUID().toString();m.put(token,new Ticket(c,scope.region(),scope.compartment(),Instant.now().plusSeconds(1800)));model.addAttribute("ticket",token);}
        }catch(BusinessException e){model.addAttribute("projectError",e.getMessage());}
        return "deployment-environments";
    }
    @PostMapping("/environments") String create(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam String project,@RequestParam String name,@RequestParam(defaultValue="") String description,@RequestParam(required=false) List<String> instances){
        Ticket t;synchronized(session){t=tickets(session).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);
        if(!c.equals(t.connection())||session.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();
        try{var id=environments.create(c,t.region(),t.compartment(),project,name,description,instances,ticket);flash.addFlashAttribute("message","환경 생성 요청 접수 · OCI 상태를 확인하세요.");return "redirect:/environments?environment="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);}
        catch(BusinessException e){flash.addFlashAttribute("message",e.getMessage());return "redirect:/environments";}
    }
    @ExceptionHandler(BusinessException.class) @ResponseBody org.springframework.http.ResponseEntity<String> error(BusinessException e){return org.springframework.http.ResponseEntity.status(409).body(e.getMessage());}
}
