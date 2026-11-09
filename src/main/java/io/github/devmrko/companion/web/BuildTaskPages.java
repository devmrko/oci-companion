package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller @Profile("!legacy")
public class BuildTaskPages {
    private final CatalogPages catalog;private final BuildTaskStore store;private final FunctionBuilds functions;private final BuildHistory builds;private final CatalogService sources;private final TaskSources taskSources;private final SourceVersions versions;private final BuildDefinition definitions;
    public BuildTaskPages(CatalogPages catalog,BuildTaskStore store,FunctionBuilds functions,BuildHistory builds,CatalogService sources,TaskSources taskSources,SourceVersions versions,BuildDefinition definitions){this.taskSources=taskSources;this.versions=versions;this.definitions=definitions;this.catalog=catalog;this.store=store;this.functions=functions;this.builds=builds;this.sources=sources;}
    @ExceptionHandler(io.github.devmrko.foundation.core.error.BusinessException.class) org.springframework.web.servlet.ModelAndView invalid(io.github.devmrko.foundation.core.error.BusinessException error) {
        var view=new org.springframework.web.servlet.ModelAndView("error");view.setStatus(error.code().equals("SCOPE")?org.springframework.http.HttpStatus.CONFLICT:org.springframework.http.HttpStatus.BAD_REQUEST);view.addObject("message",error.getMessage());return view;
    }
    @GetMapping("/builds/tasks") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="tasks") String view,@RequestParam(defaultValue="") String edit) {
        if(!Set.of("tasks","new","history","environments").contains(view))throw Errors.INVALID.exception();
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var s=catalog.selection(c,request.getSession());
        model.addAttribute("connection",c);model.addAttribute("scopeBack","/builds/tasks");model.addAttribute("selection",s);model.addAttribute("tab",view.equals("new")?"tasks":view);model.addAttribute("taskView",view);
        model.addAttribute("tasks",List.of());model.addAttribute("sourceChoices",List.of());model.addAttribute("appChoices",List.of());model.addAttribute("pipelineChoices",List.of());model.addAttribute("functionChoices",List.of());model.addAttribute("task",new BuildTaskStore.Task("","","DEVOPS","","","","","","verify","",".",""));
        if(request.getSession().getAttribute("scopeInvalid")!=null)return "build-tasks";
        try {
            var tasks=store.list(c,s.region(),s.compartment());model.addAttribute("tasks",tasks);
            if(!edit.isEmpty())model.addAttribute("task",tasks.stream().filter(t->t.id().equals(edit)).findFirst().orElseThrow(Errors.MISSING::exception));
            if(view.equals("new")) {
                try{model.addAttribute("sourceChoices",sources.sources(c,s.region(),s.compartment()));}catch(RuntimeException e){model.addAttribute("sourceError","등록 소스 조회 실패 · URL 직접 입력 가능");}
                try{model.addAttribute("appChoices",functions.applications(c,s.region(),s.compartment()));}catch(RuntimeException e){model.addAttribute("functionError","Functions 조회 실패 · 연결·권한 확인");}
                try{model.addAttribute("pipelineChoices",builds.pipelines(c,s.region(),s.compartment()));}catch(RuntimeException e){model.addAttribute("pipelineError","파이프라인 조회 실패 · 연결·권한 확인");}
                var t=(BuildTaskStore.Task)model.getAttribute("task");
                if(!t.application().isEmpty())try{model.addAttribute("functionChoices",functions.builders(c,s.region(),s.compartment(),t.application()));}catch(RuntimeException e){model.addAttribute("functionError","Function 조회 실패 · 환경 관리에서 확인");}
            }
        }catch(RuntimeException e){model.addAttribute("message",e.getMessage());}
        return "build-tasks";
    }
    @GetMapping("/builds/tasks/environments") @ResponseBody Map<String,Object> environments(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="") String application) {
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);check(session,s,region,compartment);
        var data=new HashMap<String,Object>();var warnings=new ArrayList<String>();
        try{data.put("applications",functions.applications(c,region,compartment));data.put("functions",application.isEmpty()?List.of():functions.builders(c,region,compartment,application));}
        catch(RuntimeException e){data.put("applications",List.of());data.put("functions",List.of());warnings.add("Functions 조회 실패 · 권한 확인");}
        try{data.put("pipelines",builds.pipelines(c,region,compartment));}
        catch(RuntimeException e){data.put("pipelines",List.of());warnings.add("DevOps 조회 실패 · 권한 확인");}
        data.put("warning",String.join(" / ",warnings));return data;
    }
    @GetMapping("/builds/tasks/sources") @ResponseBody Object sourceList(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment) {
        var c=SecurityConfig.connection(auth);check(session,catalog.selection(c,session),region,compartment);return sources.sources(c,region,compartment);
    }
    @GetMapping("/builds/tasks/source-versions") @ResponseBody Object sourceVersions(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,
        @RequestParam(defaultValue="") String sourceId,@RequestParam(defaultValue="") String source,@RequestParam(defaultValue="") String ref) {
        var c=SecurityConfig.connection(auth);check(session,catalog.selection(c,session),region,compartment);var selected=taskSources.resolve(c,region,compartment,sourceId,source,ref);
        return ref.isEmpty()?versions.refs(c,region,compartment,selected):versions.commits(c,region,compartment,selected,ref);
    }
    @GetMapping("/builds/tasks/source-match") @ResponseBody Object sourceMatch(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,
        @RequestParam(defaultValue="") String sourceId,@RequestParam String source,@RequestParam String pipeline) {
        var c=SecurityConfig.connection(auth);check(session,catalog.selection(c,session),region,compartment);
        var selected=taskSources.resolve(c,region,compartment,sourceId,source,"");var definition=definitions.read(c,region,compartment,pipeline);
        var matched=TaskSources.match(selected,definition);return Map.of("source",matched,"message","소스 일치");
    }
    private void check(HttpSession session,CatalogPages.Selection s,String region,String compartment){if(session.getAttribute("scopeInvalid")!=null || !s.region().equals(region) || !s.compartment().equals(compartment))throw Errors.SCOPE.exception();}
    @PostMapping("/builds/tasks/save") String save(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,
        @RequestParam(defaultValue="") String id,@RequestParam String name,@RequestParam String runner,@RequestParam(defaultValue="") String application,@RequestParam(defaultValue="") String function,@RequestParam(defaultValue="") String pipeline,
        @RequestParam(defaultValue="") String source,@RequestParam(defaultValue="") String sourceId,@RequestParam(defaultValue="") String branch,@RequestParam(defaultValue="") String commit,@RequestParam(defaultValue="verify") String goal,@RequestParam(defaultValue="") String profiles,@RequestParam(defaultValue=".") String directory,@RequestParam(defaultValue="") String artifact,@RequestParam(defaultValue="UNSPECIFIED") String target,@RequestParam(defaultValue="UNKNOWN") String architecture) {
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);check(session,s,region,compartment);
        if(!Set.of("UNSPECIFIED","FILE","VM","CONTAINER","FUNCTION").contains(target) || !Set.of("UNKNOWN","X86_64","ARM64").contains(architecture))throw Errors.INVALID.exception();
        if(runner.equals("FUNCTIONS") && Set.of("CONTAINER","FUNCTION").contains(target))throw Errors.INVALID.exception();
        if(name.isBlank() || name.length()>100 || name.chars().anyMatch(Character::isISOControl))throw Errors.INVALID.exception();
        if(!id.isEmpty() && store.list(c,region,compartment).stream().noneMatch(t->t.id().equals(id)))throw Errors.MISSING.exception();
        var selectedSource=taskSources.resolve(c,region,compartment,sourceId,source,branch);source=selectedSource.url();
        if(!commit.matches("[0-9a-f]{40}") || branch.isBlank() || branch.length()>255 || branch.chars().anyMatch(Character::isISOControl))throw Errors.INVALID.exception();
        if(runner.equals("FUNCTIONS")) {
            if(selectedSource.provider().equals("OCI"))throw Errors.INVALID.exception();
            FunctionTemplate.request(UUID.randomUUID().toString(),source,commit,goal,profiles,directory,artifact);
            var b=functions.read(c,region,compartment,application,function);if(!b.compatible() || !io.github.devmrko.companion.port.FunctionBuilds.CONTRACT.equals(b.contract()) || !b.state().equals("ACTIVE"))throw Errors.INVALID.exception();pipeline="";
        }else if(runner.equals("DEVOPS")) {
            String selected=pipeline;if(builds.pipelines(c,region,compartment).stream().noneMatch(p->p.id().equals(selected)))throw Errors.MISSING.exception();
            var matched=TaskSources.match(selectedSource,definitions.read(c,region,compartment,pipeline));
            if(definitions.branches(c,region,matched).stream().noneMatch(b->b.value().equals(branch)))throw Errors.BUILD_PARAMETERS.exception();
            application="";function="";goal="";profiles="";directory="";artifact="";
        }else throw Errors.INVALID.exception();
        store.save(c,region,compartment,new BuildTaskStore.Task(id.isEmpty()?UUID.randomUUID().toString():id,name.strip(),runner,application,function,pipeline,source,commit,goal,profiles,directory,artifact,sourceId,branch,target,architecture));
        return "redirect:/builds/tasks";
    }
    @PostMapping("/builds/tasks/delete") String delete(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam String id) {
        var c=SecurityConfig.connection(auth);check(session,catalog.selection(c,session),region,compartment);store.delete(c,region,compartment,id);return "redirect:/builds/tasks";
    }
}
