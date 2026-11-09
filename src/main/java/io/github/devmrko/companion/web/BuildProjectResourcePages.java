package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.domain.Models.Connection;
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
public class BuildProjectResourcePages {
    private final CatalogPages catalog;private final BuildAdministration admin;private final BuildProjectResources resources;
    public BuildProjectResourcePages(CatalogPages catalog,BuildAdministration admin,BuildProjectResources resources){this.catalog=catalog;this.admin=admin;this.resources=resources;}
    record Ticket(Connection connection,String region,String compartment,String project,Instant expires) {}
    @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession session){var map=(Map<String,Ticket>)session.getAttribute("projectResourceTickets");if(map==null){map=new LinkedHashMap<>();session.setAttribute("projectResourceTickets",map);}return map;}
    @GetMapping("/builds/project-resources") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String project){
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var scope=catalog.selection(c,request.getSession());model.addAttribute("connection",c);model.addAttribute("tab","projects");model.addAttribute("scopeBack","/builds/project-resources");model.addAttribute("project",project);model.addAttribute("projects",List.of());
        if(request.getSession().getAttribute("scopeInvalid")!=null)return "build-project-resources";
        try{var projects=admin.projects(c,scope.region(),scope.compartment());model.addAttribute("projects",projects);if(!project.isBlank()){
            model.addAttribute("selectedProject",projects.stream().filter(p->p.id().equals(project)).findFirst().orElseThrow(Errors.SCOPE::exception));
            model.addAttribute("resources",resources.read(c,scope.region(),scope.compartment(),project));
            synchronized(request.getSession()){var items=tickets(request.getSession());if(items.size()>=32)items.remove(items.keySet().iterator().next());String key=UUID.randomUUID().toString();items.put(key,new Ticket(c,scope.region(),scope.compartment(),project,Instant.now().plusSeconds(1800)));model.addAttribute("ticket",key);}
        }}catch(BusinessException e){model.addAttribute("resourceError",e.getMessage());}return "build-project-resources";
    }
    @PostMapping("/builds/project-resources") String create(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam String action,@RequestParam String name,@RequestParam(defaultValue="") String description,@RequestParam(defaultValue="") String group){
        Ticket t;synchronized(session){t=tickets(session).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);if(!c.equals(t.connection())||session.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();
        try{switch(action){case "repository"->resources.createRepository(c,t.region(),t.compartment(),t.project(),name,description,ticket);case "group"->resources.createLogGroup(c,t.region(),t.compartment(),t.project(),name,ticket);case "log"->resources.createLog(c,t.region(),t.compartment(),t.project(),group,name,ticket);default->throw Errors.INVALID.exception();}flash.addFlashAttribute("message","생성 요청 접수 · 아래 OCI 조회 결과에서 상태를 확인하세요.");}catch(BusinessException e){flash.addFlashAttribute("message",e.getMessage());}
        return "redirect:/builds/project-resources?project="+java.net.URLEncoder.encode(t.project(),java.nio.charset.StandardCharsets.UTF_8);
    }
    @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
