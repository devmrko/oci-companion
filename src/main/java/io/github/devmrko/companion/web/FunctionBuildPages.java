package io.github.devmrko.companion.web;

import io.github.devmrko.companion.port.FunctionBuilds;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.foundation.core.error.BusinessException;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @Profile("!legacy")
public class FunctionBuildPages {
    private final FunctionBuilds builds;
    private final io.github.devmrko.companion.port.BuildTaskStore taskStore;
    private final CatalogPages catalog;
    private final io.github.devmrko.companion.port.Registry registry;
    public FunctionBuildPages(FunctionBuilds builds,CatalogPages catalog,io.github.devmrko.companion.port.Registry registry,io.github.devmrko.companion.port.BuildTaskStore taskStore) { this.taskStore=taskStore; this.builds=builds;this.catalog=catalog;this.registry=registry; }
    @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView invalid(BusinessException error) {
        var view=new org.springframework.web.servlet.ModelAndView("error");
        view.setStatus(Set.of("SCOPE","CONFLICT","BUILD_CHANGED").contains(error.code())?HttpStatus.CONFLICT:HttpStatus.BAD_REQUEST);
        view.addObject("message",error.getMessage());return view;
    }
    record Ticket(String region,String compartment,String application,String function,String etag,String run,String contract) {}
    @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession session) {
        var value=(Map<String,Ticket>)session.getAttribute("functionTickets");
        if(value==null){value=new LinkedHashMap<>();session.setAttribute("functionTickets",value);}return value;
    }
    @SuppressWarnings("unchecked") private Map<String,String> submittedProfiles(HttpSession session) {
        var value=(Map<String,String>)session.getAttribute("functionSubmittedProfiles");
        if(value==null){value=new LinkedHashMap<>();session.setAttribute("functionSubmittedProfiles",value);}return value;
    }
    @GetMapping("/builds/functions") String page(Authentication auth,HttpServletRequest request,Model model,
        @RequestParam(defaultValue="environments") String view,@RequestParam(defaultValue="") String application,
        @RequestParam(defaultValue="") String function,@RequestParam(defaultValue="") String run,@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="") String task) {
        if(!Set.of("environments","execute","history","new").contains(view))throw Errors.INVALID.exception();
        var c=SecurityConfig.connection(auth);var session=request.getSession();
        catalog.navigation(auth,request,model);model.addAttribute("connection",c);model.addAttribute("scopeBack","/builds/functions");
        model.addAttribute("tab",view.equals("new")?"environments":view);model.addAttribute("view",view);model.addAttribute("functionMode",true);
        model.addAttribute("project","");model.addAttribute("pipeline","");model.addAttribute("functionApplication",application);model.addAttribute("function",function);
        model.addAttribute("applications",List.of());model.addAttribute("builders",List.of());model.addAttribute("functionRuns",List.of());model.addAttribute("loaded",false);model.addAttribute("buildersLoaded",false);model.addAttribute("historyLoaded",false);model.addAttribute("selectedRun",run);
        try {
            var s=catalog.selection(c,session);model.addAttribute("selection",s);model.addAttribute("permissionRules",PermissionHelp.rules("functions",s.compartment()));
            if(session.getAttribute("scopeInvalid")!=null)return "build-functions";
            if(!task.isEmpty()) {
                var saved=taskStore.list(c,s.region(),s.compartment()).stream().filter(t->t.id().equals(task)).findFirst().orElseThrow(Errors.MISSING::exception);
                if(!saved.runner().equals("FUNCTIONS") || !saved.application().equals(application) || !saved.function().equals(function))throw Errors.SCOPE.exception();
                model.addAttribute("buildTask",saved);
            }
            var apps=builds.applications(c,s.region(),s.compartment());model.addAttribute("applications",apps);model.addAttribute("loaded",true);
            if(view.equals("new")) {
                try {model.addAttribute("functionSubnets",builds.subnets(c,s.region(),s.compartment()));}
                catch(BusinessException e){model.addAttribute("subnetError","서브넷 조회 실패 · OCID를 직접 입력하거나 네트워크 읽기 권한을 확인하세요.");}
                try { model.addAttribute("registryPrefixes",registry.list(c,s.region(),s.compartment(),"CONTAINER").stream().map(r->r.name()).filter(n->n.contains("/")).map(n->n.substring(0,n.lastIndexOf('/'))).distinct().sorted().toList()); }
                catch(BusinessException e){model.addAttribute("registryError","Container Registry 조회 실패 · 경로를 직접 입력하거나 읽기 권한을 확인하세요.");}
                try { model.addAttribute("storageOptions",builds.storageOptions(c,s.region(),s.compartment())); }
                catch(BusinessException e){model.addAttribute("storageError","버킷 조회 실패 · 이름을 직접 입력하거나 Object Storage 읽기 권한을 확인하세요.");}
            }
            if(!application.isEmpty()) {
                var selected=apps.stream().filter(a->a.id().equals(application)).findFirst().orElseThrow(()->Errors.MISSING.exception());model.addAttribute("selectedApplication",selected);
                var builders=builds.builders(c,s.region(),s.compartment(),application);model.addAttribute("builders",builders);model.addAttribute("buildersLoaded",true);
                if(!function.isEmpty()) {
                    var builder=builders.stream().filter(b->b.id().equals(function)).findFirst().orElseThrow(()->Errors.MISSING.exception());model.addAttribute("builder",builder);
                    if(builder.compatible() && view.equals("execute")) {
                        String ticket=UUID.randomUUID().toString();
                        synchronized(session){var map=tickets(session);if(map.size()>=32)map.remove(map.keySet().iterator().next());map.put(ticket,new Ticket(s.region(),s.compartment(),application,function,builder.etag(),UUID.randomUUID().toString(),builder.contract()));}
                        model.addAttribute("functionTicket",ticket);
                    }
                    if(builder.compatible() && view.equals("history")) {
                        var results=builds.runs(c,s.region(),s.compartment(),application,function,cursor);var runs=results.items();model.addAttribute("functionRuns",runs);model.addAttribute("functionNext",results.next());model.addAttribute("cursor",cursor);model.addAttribute("historyLoaded",true);

                    }
                }
            }
        }catch(BusinessException | IllegalStateException e){model.addAttribute("message",e.getMessage());}
        return "build-functions";
    }
    @GetMapping("/builds/functions/progress") @ResponseBody ResponseEntity<Map<String,String>> progress(Authentication auth,HttpSession session,
        @RequestParam String region,@RequestParam String compartment,@RequestParam String application,@RequestParam String function,@RequestParam String run) {
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);
        if(session.getAttribute("scopeInvalid")!=null || !region.equals(s.region()) || !compartment.equals(s.compartment()))throw Errors.SCOPE.exception();
        var result=builds.run(c,region,compartment,application,function,run);
        var log=builds.log(c,region,compartment,application,function,run);
        String profiles;synchronized(session){profiles=submittedProfiles(session).getOrDefault(String.join("/",region,compartment,application,function,run),"");}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status",result.status(),"result",result.result(),"log",log,"profiles",profiles));
    }
    @GetMapping("/builds/functions/reports") @ResponseBody ResponseEntity<TestReports.Summary> reports(Authentication auth,HttpSession session,
        @RequestParam String region,@RequestParam String compartment,@RequestParam String application,@RequestParam String function,@RequestParam String run) {
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);
        if(session.getAttribute("scopeInvalid")!=null || !region.equals(s.region()) || !compartment.equals(s.compartment()))throw Errors.SCOPE.exception();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(builds.reports(c,region,compartment,application,function,run));
    }
    @PostMapping("/builds/functions/template") ResponseEntity<byte[]> template(Authentication auth,HttpSession session,
        @RequestParam String name,@RequestParam String application,@RequestParam String architecture,@RequestParam String namespace,
        @RequestParam String bucket,@RequestParam(defaultValue="") String subnet,@RequestParam(defaultValue="builders") String registryPrefix) {
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);
        if(session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        var files=FunctionTemplate.files(c.profile(),s.region(),s.compartment(),new FunctionTemplate.Settings(name,application,architecture,namespace,bucket,subnet,registryPrefix));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+".zip\"").body(FunctionTemplate.zip(files));
    }
    @PostMapping("/builds/functions/run") String run(Authentication auth,HttpSession session,RedirectAttributes flash,
        @RequestParam String ticket,@RequestParam(defaultValue="") String source,@RequestParam String commit,@RequestParam String goal,
        @RequestParam(defaultValue="") String profiles,@RequestParam(defaultValue=".") String directory,@RequestParam(defaultValue="") String artifact,
        @RequestParam(defaultValue="") String sourceObject,@RequestParam(defaultValue="") String sourceVersion,
        @RequestParam(defaultValue="") String sourceSha256,@RequestParam(defaultValue="") String requiredFreeBytes) {
        var c=SecurityConfig.connection(auth);Ticket t;synchronized(session){t=tickets(session).remove(ticket);}if(t==null)throw Errors.CONFLICT.exception();
        var s=catalog.selection(c,session);
        if(!s.region().equals(t.region()) || !s.compartment().equals(t.compartment()) || session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        String payload;
        if("python-v1".equals(t.contract())) {
            if(!source.isEmpty() || !profiles.isEmpty())throw Errors.INVALID.exception();
            payload=PythonFunctionRequest.request(t.run(),sourceObject,sourceVersion,sourceSha256,commit,goal,directory,artifact,requiredFreeBytes);
        } else {
            if(!sourceObject.isEmpty() || !sourceVersion.isEmpty() || !sourceSha256.isEmpty() || !requiredFreeBytes.isEmpty())throw Errors.INVALID.exception();
            payload=FunctionTemplate.request(t.run(),source,commit,goal,profiles,directory,artifact);
        }
        synchronized(session){var values=submittedProfiles(session);if(values.size()>=64)values.remove(values.keySet().iterator().next());values.put(String.join("/",t.region(),t.compartment(),t.application(),t.function(),t.run()),profiles);}
        try {builds.invoke(c,t.region(),t.compartment(),t.application(),t.function(),t.etag(),payload);flash.addFlashAttribute("message","빌드 접수 · "+t.run()+" · 결과가 기록되면 조회됩니다.");}
        catch(BusinessException e){flash.addFlashAttribute("message",e.getMessage()+" 실행 ID: "+t.run());}
        return "redirect:/builds/functions?view=history&application="+t.application()+"&function="+t.function()+"&run="+t.run()+"#function-progress";
    }
}
