package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.*;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.foundation.core.error.BusinessException;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @Profile("!legacy")
public class CatalogPages {
    private final CloudConnection cloud;
    private final ResourceCatalog catalog;
    private final CatalogService service;
    private final SecurityContextRepository contexts;
    private final GuideCatalog guides;
    private final BuildHistory builds;
    private final BuildLogs logs;
    private final BuildSetup setup;
    private final Registry registry;
    private final BuildAdministration administration;
    private final SelectionStore selections;
    private final BuildControl control;
    private final BuildDefinition definitions;
    private final BuildInputStore inputRules;
    private final BuildPresetStore presets;
    private final BuildTaskStore taskStore;
    private final TaskSources taskSources;
    public CatalogPages(CloudConnection cloud,ResourceCatalog catalog,CatalogService service,SecurityContextRepository contexts,GuideCatalog guides,BuildHistory builds,SelectionStore selections,BuildControl control,BuildPresetStore presets,BuildDefinition definitions,BuildInputStore inputRules,BuildAdministration administration,BuildLogs logs,BuildSetup setup,Registry registry,BuildTaskStore taskStore,TaskSources taskSources) {
        this.taskStore=taskStore;this.taskSources=taskSources;
        this.registry=registry;this.setup=setup;this.logs=logs;this.administration=administration;this.cloud=cloud; this.catalog=catalog; this.service=service; this.contexts=contexts; this.guides=guides;this.builds=builds;this.selections=selections;this.control=control;this.presets=presets;this.definitions=definitions;this.inputRules=inputRules;
    }
    @ModelAttribute void navigation(Authentication auth,HttpServletRequest request,Model model) {
        if(auth!=null && auth.getPrincipal() instanceof Connection c && request.getMethod().equals("GET")) {
            try {
                model.addAttribute("navSelection",selection(c,request.getSession()));
                model.addAttribute("scopeMessage",request.getSession().getAttribute("scopeInvalid"));
            } catch(BusinessException | IllegalStateException e) { model.addAttribute("scopeMessage","범위 조회 실패 · 연결과 권한을 확인하세요."); }
            String menu=request.getRequestURI().equals("/builds")?Objects.toString(request.getParameter("tab"),"execute"):request.getRequestURI().equals("/guides")?"guides":"catalog";
            var nav=(Selection)model.getAttribute("navSelection");
            model.addAttribute("permissionRules",PermissionHelp.rules(menu,nav==null?null:nav.compartment()));
            if(menu.equals("registry"))model.addAttribute("scopeBack","/builds?tab=registry&kind="+("CONTAINER".equals(request.getParameter("kind"))?"CONTAINER":"GENERIC"));
            else model.addAttribute("scopeBack",Set.of("/sources","/builds","/guides").contains(request.getRequestURI())?request.getRequestURI():"/");
        }
    }
    @ModelAttribute("connection") Connection connection(Authentication auth) { return auth!=null && auth.getPrincipal() instanceof Connection c?c:null; }
    @GetMapping("/login") String login(Model model) { model.addAttribute("profiles",cloud.profiles()); return "login"; }
    @PostMapping("/connect") String connect(@RequestParam String profile,HttpServletRequest req,HttpServletResponse res,RedirectAttributes flash) {
        try {
            var c=cloud.connect(profile);
            if (req.getSession(false)!=null) req.getSession(false).invalidate();
            if (req.getSession(false)!=null) req.changeSessionId(); else req.getSession(true);
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(c,null,List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
            SecurityContextHolder.setContext(context); contexts.saveContext(context,req,res);
            return "redirect:/";
        } catch (BusinessException e) { flash.addFlashAttribute("message",Errors.CONFIG.safeMessage()); return "redirect:/login"; }
    }

    public record Selection(List<Choice> regions,List<Choice> compartments,String region,String compartment,int interval,String mode) {}
    public record Row(Resource resource,Choice compartment) {}
    public record Registration(Source source,String compartment) {}
    public record Report(String error,long elapsedMs) {}
    public record ScopeResult(Choice compartment,String updatedAt,String error,Long elapsedMs) {}
    @SuppressWarnings("unchecked")
    Selection selection(Connection c,HttpSession session) {
        var regions=(List<Choice>)session.getAttribute("regions");
        var compartments=(List<Choice>)session.getAttribute("compartments");
        if (regions==null || compartments==null) {
            regions=catalog.regions(c); compartments=catalog.compartments(c);
            if (regions.isEmpty() || compartments.isEmpty()) throw Errors.MISSING.exception();
            session.setAttribute("regions",regions); session.setAttribute("compartments",compartments);
        }
        if(session.getAttribute("scopeLoaded")==null) {
            try {
                var saved=selections.read(c);
                if(saved.isPresent()) {
                    var value=saved.get();
                    if(regions.stream().anyMatch(r->r.id().equals(value.region())) && compartments.stream().anyMatch(t->t.id().equals(value.compartment()))) {
                        session.setAttribute("region",value.region());session.setAttribute("compartment",value.compartment());
                    } else session.setAttribute("scopeInvalid","저장한 리전·구획에 접근할 수 없습니다. 다시 선택하세요.");
                }
            } catch(IllegalStateException e) { session.setAttribute("scopeInvalid",e.getMessage()); }
            session.setAttribute("scopeLoaded",true);
        }
        String region=(String)session.getAttribute("region");
        if (region==null) region=regions.stream().anyMatch(r->r.id().equals(c.region()))?c.region():regions.getFirst().id();
        String compartment=(String)session.getAttribute("compartment");
        if (compartment==null) compartment=compartments.getFirst().id();
        Integer interval=(Integer)session.getAttribute("interval");
        String mode=(String)session.getAttribute("scopeMode");
        return new Selection(regions,compartments,region,compartment,interval==null?0:interval,mode==null?"selected":mode);
    }
    private List<Choice> targets(Selection s) { return CompartmentScopes.targets(s.compartments(),s.compartment(),s.mode()); }
    private List<Row> rows(Connection c,Selection s) {
        var rows=new ArrayList<Row>();
        for (var target:targets(s)) {
            var view=service.read(c,s.region(),target.id());
            if (view.snapshot()!=null) for (var resource:view.snapshot().resources()) rows.add(new Row(resource,target));
        }
        return rows;
    }
    @SuppressWarnings("unchecked")
    @GetMapping({"/","/sources"}) String page(Authentication auth,HttpSession session,HttpServletRequest request,Model model,
        @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String project,@RequestParam(defaultValue="") String state) {
        var c=SecurityConfig.connection(auth);
        boolean sourcePage=request.getRequestURI().equals("/sources");
        model.addAttribute("sourcePage",sourcePage);model.addAttribute("q",q);model.addAttribute("project",project);model.addAttribute("state",state);
        try {
            var s=selection(c,session); model.addAttribute("selection",s);
            if(session.getAttribute("scopeInvalid")!=null) { model.addAttribute("selection",null);return "catalog"; }
            var reports=(Map<String,Report>)session.getAttribute("scopeReports");
            var results=new ArrayList<ScopeResult>();
            var registrations=new LinkedHashMap<String,Registration>();
            for (var target:targets(s)) {
                var view=service.read(c,s.region(),target.id());
                var report=reports==null?null:reports.get(service.scope(c,s.region(),target.id()));
                results.add(new ScopeResult(target,view.snapshot()==null?null:view.snapshot().updatedAt(),
                    view.error()!=null?view.error():report==null?null:report.error(),report==null?null:report.elapsedMs()));
                for (var source:service.sources(c,s.region(),target.id())) registrations.putIfAbsent(source.id(),new Registration(source,target.id()));
            }
            var all=rows(c,s);
            var projects=new LinkedHashMap<String,String>();
            all.stream().filter(r -> r.resource().kind().equals("PROJECT")).forEach(r -> projects.put(r.resource().id(),r.resource().name()));
            model.addAttribute("projects",projects);
            var candidates=all.stream().filter(r -> !sourcePage || r.resource().kind().equals("REPOSITORY")).toList();
            model.addAttribute("states",candidates.stream().map(r -> r.resource().state()).filter(Objects::nonNull).distinct().sorted().toList());
            model.addAttribute("rows",candidates.stream().filter(r -> q.isBlank() || Objects.toString(r.resource().name(),"").toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))
                .filter(r -> project.isEmpty() || project.equals(r.resource().projectId()) || (r.resource().kind().equals("PROJECT") && project.equals(r.resource().id())))
                .filter(r -> state.isEmpty() || state.equals(r.resource().state())).toList());
            model.addAttribute("scopeResults",results);model.addAttribute("sources",registrations.values());
            model.addAttribute("cli",cli(c,s));
        } catch (BusinessException e) { model.addAttribute("message","범위 조회 실패 · IAM 권한과 연결 확인"); }
          catch (IllegalStateException e) { model.addAttribute("message",e.getMessage()); }
        return "catalog";
    }
    @PostMapping("/scope") String scope(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="0") int interval,@RequestParam(defaultValue="selected") String mode,@RequestParam(defaultValue="/") String back) {
        var s=selection(SecurityConfig.connection(auth),session);
        if (s.regions().stream().noneMatch(r->r.id().equals(region)) || s.compartments().stream().noneMatch(r->r.id().equals(compartment)) || !Set.of(0,60,300,900).contains(interval)) throw Errors.INVALID.exception();
        CompartmentScopes.targets(s.compartments(),compartment,mode);
        String destination=Set.of("/agents","/environments","/deployments","/sources","/builds","/guides","/builds/functions","/builds/tasks","/builds?tab=registry&kind=GENERIC","/builds?tab=registry&kind=CONTAINER").contains(back)?back:"/";
        try { selections.write(SecurityConfig.connection(auth),region,compartment); }
        catch(IllegalStateException e) { flash.addFlashAttribute("message",e.getMessage());return "redirect:"+destination; }
        if(!s.region().equals(region) || !s.compartment().equals(compartment)) session.removeAttribute("scopeReports");
        session.removeAttribute("scopeInvalid");
        session.setAttribute("region",region); session.setAttribute("compartment",compartment); session.setAttribute("interval",mode.equals("selected")?interval:0);session.setAttribute("scopeMode",mode);
        return "redirect:"+destination;
    }
    @GetMapping("/builds") String builds(Authentication auth,HttpSession session,Model model,
        @RequestParam(defaultValue="") String pipeline,@RequestParam(defaultValue="") String run,
        @RequestParam(defaultValue="") String tab,@RequestParam(defaultValue="") String preset,@RequestParam(defaultValue="") String project,
        @RequestParam(defaultValue="") String topic,@RequestParam(defaultValue="GENERIC") String kind,
        @RequestParam(defaultValue="") String repository,@RequestParam(defaultValue="") String page,@RequestParam(defaultValue="") String task,@RequestParam(defaultValue="") String registeredSource) {
        var c=SecurityConfig.connection(auth);
        if(tab.isEmpty() && pipeline.isEmpty() && run.isEmpty())return "redirect:/builds/tasks";
        if(tab.isEmpty()) tab=run.isEmpty()?"execute":"history";
        if(!Set.of("registry","topics","projects","pipelines","execute","history").contains(tab)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        model.addAttribute("pipeline",pipeline);model.addAttribute("registryItems",List.of());model.addAttribute("registryLoaded",false);model.addAttribute("kind",kind);model.addAttribute("repository",repository);
        model.addAttribute("topicItems",List.of());model.addAttribute("selectedTopic",topic);model.addAttribute("topicsLoaded",false);
        model.addAttribute("projectItems",List.of());model.addAttribute("topics",List.of());model.addAttribute("projects",Map.of());model.addAttribute("project",project);model.addAttribute("pipelines",List.of());model.addAttribute("runs",List.of());model.addAttribute("tab",tab);
        try {
            var s=selection(c,session);model.addAttribute("selection",s);
            if(!registeredSource.isEmpty())model.addAttribute("handoffSource",service.sources(c,s.region(),s.compartment()).stream().filter(v->v.id().equals(registeredSource)).findFirst().orElseThrow(Errors.MISSING::exception));
            if(session.getAttribute("scopeInvalid")!=null) return "builds";
            if(tab.equals("registry")) {
                if(!Set.of("GENERIC","CONTAINER").contains(kind))throw Errors.REGISTRY_INPUT.exception();
                model.addAttribute("registryTicket",registryTicket(session,s,kind,pipeline,null));
                model.addAttribute("registryItems",registry.list(c,s.region(),s.compartment(),kind));
                model.addAttribute("registryLoaded",true);
                String args=" --profile "+quote(c.profile())+" --region "+quote(s.region())+" --compartment-id "+quote(s.compartment());
                String command=kind.equals("GENERIC")?"repository":"container repository";
                model.addAttribute("registryListCli","oci artifacts "+command+" list"+args+" --all");
                model.addAttribute("registryCreateCli","oci artifacts "+command+" create"+args+(kind.equals("GENERIC")?" --repository-type GENERIC":" --is-public false")+" --display-name '<저장소 이름>'"+(kind.equals("GENERIC")?" --is-immutable true":""));
                if(!repository.isEmpty()) {
                    var detail=registry.read(c,s.region(),s.compartment(),kind,repository);
                    model.addAttribute("registryDetail",detail);
                    String identity=" --profile "+quote(c.profile())+" --region "+quote(s.region())+" --repository-id "+quote(repository);
                    model.addAttribute("registryGetCli","oci artifacts "+command+" get"+identity);
                    model.addAttribute("registryUpdateCli","oci artifacts "+command+" update"+identity+(kind.equals("GENERIC")?" --description '<설명>'":" --readme file://readme.json"));
                    model.addAttribute("registryArtifactsCli","oci artifacts "+(kind.equals("GENERIC")?"generic artifact":"container image")+" list"+args+" --repository-id "+quote(repository)+" --all");
                    model.addAttribute("registryEditTicket",registryTicket(session,s,kind,pipeline,detail));
                    try {model.addAttribute("registryArtifacts",registry.artifacts(c,s.region(),s.compartment(),kind,repository,page));}
                    catch(BusinessException e){model.addAttribute("artifactError",e.getMessage());}
                }
                return "build-registry";
            }
            if(tab.equals("topics")) {
                model.addAttribute("adminTicket",adminTicket(session,s,tab));
                String args=" --profile "+quote(c.profile())+" --region "+quote(s.region())+" --compartment-id "+quote(s.compartment());
                model.addAttribute("topicListCli","oci ons topic list"+args+" --all");
                model.addAttribute("topicCreateCli","oci ons topic create"+args+" --name '<주제 이름>'");
                model.addAttribute("topicItems",administration.topicDetails(c,s.region(),s.compartment()));
                model.addAttribute("topicsLoaded",true);
                return "build-topics";
            }
            if(tab.equals("projects") || tab.equals("pipelines")) {
                model.addAttribute("projectItems",administration.projects(c,s.region(),s.compartment()));
                model.addAttribute("adminTicket",adminTicket(session,s,tab));
                if(tab.equals("projects")) {
                    model.addAttribute("topics",List.of());
                    try { model.addAttribute("topics",administration.topics(c,s.region(),s.compartment())); }
                    catch(BusinessException e) { model.addAttribute("topicError","알림 주제 조회 실패 · Notifications 읽기 권한을 확인하세요."); }
                    return "build-projects";
                }
            }
            var all=builds.pipelines(c,s.region(),s.compartment());
            var projects=new LinkedHashMap<String,String>();all.forEach(p->projects.put(p.projectId(),p.projectName()));
            if(tab.equals("pipelines")) {
                projects.clear();
                @SuppressWarnings("unchecked") var items=(List<BuildAdministration.Project>)model.getAttribute("projectItems");
                items.forEach(p->projects.put(p.id(),p.name()));
            }
            model.addAttribute("projects",projects);model.addAttribute("project",project);
            var list=all.stream().filter(p->project.isEmpty() || project.equals(p.projectId())).toList();model.addAttribute("pipelines",list);
            String selected=pipeline.isEmpty() && !list.isEmpty()?list.getFirst().id():pipeline;
            model.addAttribute("pipeline",selected);
            model.addAttribute("buildCli","oci devops build-pipeline list --profile "+quote(c.profile())+" --region "+quote(s.region())+" --compartment-id "+quote(s.compartment())+" --all");
            model.addAttribute("runCli","oci devops build-run create --profile "+quote(c.profile())+" --region "+quote(s.region())+" --build-pipeline-id "+quote(selected)+" --build-run-arguments file://arguments.json");
            model.addAttribute("defaultsCli","oci devops build-pipeline update --profile "+quote(c.profile())+" --region "+quote(s.region())+" --build-pipeline-id "+quote(selected)+" --build-pipeline-parameters file://parameters.json");
            if(!selected.isEmpty()) {
                if(list.stream().noneMatch(p->p.id().equals(selected))) throw Errors.MISSING.exception();
                model.addAttribute("pipelineRule","ALL {resource.type = 'devopsbuildpipeline', resource.id = '"+selected+"'}");
                if(tab.equals("execute") || tab.equals("pipelines")) {
                    var definition=definitions.read(c,s.region(),s.compartment(),selected);
                    model.addAttribute("definition",definition);
                    var specs=new LinkedHashMap<String,BuildDefinition.Specification>();
                    for(var stage:definition.stages()) if(!stage.spec().isEmpty()) {
                        var primary=stage.sources().stream().filter(BuildDefinition.Source::primary).findFirst();
                        if(primary.isEmpty() && stage.sources().size()==1) primary=Optional.of(stage.sources().getFirst());
                        if(primary.isPresent()) specs.put(stage.id(),definitions.specification(c,s.region(),primary.get(),stage.spec()));
                    }
                    model.addAttribute("specifications",specs);
                    if(tab.equals("pipelines") || tab.equals("execute")) {
                        var value=setup.read(c,s.region(),s.compartment(),selected);
                        model.addAttribute("setup",value);
                        model.addAttribute("selectedRepository",repository);
                        try { model.addAttribute("containerRepositories",registry.list(c,s.region(),s.compartment(),"CONTAINER").stream().filter(r->r.state().equalsIgnoreCase("AVAILABLE")).toList()); }
                        catch(BusinessException e) {model.addAttribute("containerRegistryError",e.getMessage());model.addAttribute("containerRepositories",List.of());}
                        if(value!=null) {
                            var token=UUID.randomUUID().toString();
                            synchronized(session) {
                                var map=setupTickets(session);
                                if(map.size()>=32)map.remove(map.keySet().iterator().next());
                                map.put(token,new SetupTicket(s.region(),s.compartment(),selected,definition.revision(),value,tab));
                            }
                            model.addAttribute("setupTicket",token);
                        }
                        if(value!=null)model.addAttribute("destinationArtifacts",value.artifacts().stream().collect(java.util.stream.Collectors.toMap(BuildSetup.Artifact::id,a->a,(a,b)->a)));
                        if(tab.equals("pipelines")) {
                            try {model.addAttribute("buildOptions",setup.buildOptions(c,s.region(),s.compartment(),selected));}
                            catch(BusinessException e){model.addAttribute("buildOptionsError",e.getMessage());}
                            model.addAttribute("stageCreateCli","oci devops build-pipeline-stage create-build-stage --profile "+quote(c.profile())+" --region "+quote(s.region())+" --build-pipeline-id "+quote(selected)+" --build-source-collection file://sources.json --image OL8_X86_64_STANDARD_10 --stage-predecessor-collection file://predecessors.json --build-spec-file build_spec.yaml");
                            return "builds";
                        }
                        model.addAttribute("buildChecks",BuildChecks.inspect(definition,value,specs));
                        model.addAttribute("buildPurpose",BuildChecks.purpose(definition));
                    }
                    var settings=control.settings(c,s.region(),s.compartment(),selected);
                    var saved=presets.list(c,s.region(),s.compartment(),selected);
                    var parameters=settings.parameters();
                    var loaded=new BuildPresetStore.Preset("",parameters);
                    if(!preset.isEmpty()) loaded=saved.stream().filter(v->v.name().equals(preset)).findFirst().orElseThrow(Errors.MISSING::exception);
                    parameters=loaded.parameters();
                    final String savedSource=loaded.source().source();
                    if(!savedSource.isEmpty() && definition.sources().stream().noneMatch(v->v.key().equals(savedSource))) throw Errors.BUILD_PRESET.exception();
                    if(!task.isEmpty()) {
                        var savedTask=taskStore.list(c,s.region(),s.compartment()).stream().filter(v->v.id().equals(task)).findFirst().orElseThrow(Errors.MISSING::exception);
                        if(!savedTask.runner().equals("DEVOPS") || !savedTask.pipeline().equals(selected))throw Errors.SCOPE.exception();
                        if(!savedTask.source().isEmpty()) {
                            var chosen=taskSources.resolve(c,s.region(),s.compartment(),savedTask.sourceId(),savedTask.source(),savedTask.branch());
                            var matched=TaskSources.match(chosen,definition);
                            loaded=new BuildPresetStore.Preset(loaded.name(),loaded.parameters(),savedTask.name(),new BuildDefinition.SourceOverride(matched.key(),savedTask.branch(),savedTask.commit()));
                        }
                    }
                    model.addAttribute("runName",loaded.runName());model.addAttribute("sourceOverride",loaded.source());
                    model.addAttribute("fields",BuildInputs.fields(settings.parameters(),parameters,inputRules.read(c,s.region(),s.compartment(),selected)));
                    model.addAttribute("parameters",parameters);model.addAttribute("presets",saved);
                    model.addAttribute("presetName",preset);
                    String ticket=UUID.randomUUID().toString();
                    synchronized(session) {
                        var tickets=tickets(session);
                        if(tickets.size()>=32) tickets.remove(tickets.keySet().iterator().next());
                        tickets.put(ticket,new BuildTicket(s.region(),s.compartment(),selected,settings.etag(),definition.revision()));
                    }
                    model.addAttribute("ticket",ticket);
                    return "builds";
                }
                var runs=builds.runs(c,s.region(),s.compartment(),selected);model.addAttribute("runs",runs);
                String selectedRun=run.isEmpty() && !runs.isEmpty()?runs.getFirst().id():run;
                if(!selectedRun.isEmpty()) {
                    if(runs.stream().noneMatch(r->r.id().equals(selectedRun))) throw Errors.MISSING.exception();
                    model.addAttribute("detail",builds.detail(c,s.region(),s.compartment(),selected,selectedRun));
                }
            }
        } catch(BusinessException | IllegalStateException e) { model.addAttribute("message",e.getMessage()); }
        return tab.equals("registry")?"build-registry":tab.equals("topics")?"build-topics":tab.equals("projects")?"build-projects":"builds";
    }

    record RegistryTicket(String region,String compartment,String kind,String pipeline,Registry.Repository repository) {}
    @SuppressWarnings("unchecked")
    private Map<String,RegistryTicket> registryTickets(HttpSession session) {
        var map=(Map<String,RegistryTicket>)session.getAttribute("registryTickets");
        if(map==null){map=new LinkedHashMap<>();session.setAttribute("registryTickets",map);}return map;
    }
    private String registryTicket(HttpSession session,Selection scope,String kind,String pipeline,Registry.Repository repository) {
        synchronized(session){var map=registryTickets(session);if(map.size()>=32)map.remove(map.keySet().iterator().next());
            var token=UUID.randomUUID().toString();map.put(token,new RegistryTicket(scope.region(),scope.compartment(),kind,pipeline,repository));return token;}
    }
    @PostMapping("/builds/registry") String registryAction(Authentication auth,HttpSession session,RedirectAttributes flash,
        @RequestParam String ticket,@RequestParam String name,@RequestParam(defaultValue="") String description,@RequestParam(defaultValue="true") boolean immutable) {
        RegistryTicket t;synchronized(session){t=registryTickets(session).remove(ticket);}
        if(t==null)throw Errors.CONFLICT.exception();
        var c=SecurityConfig.connection(auth);checkScope(selection(c,session),t.region(),t.compartment());
        if(session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        String id=t.repository()==null?"":t.repository().id();
        try {
            if(t.repository()==null)id=registry.create(c,t.region(),t.compartment(),t.kind(),name,description,immutable,ticket);
            else registry.update(c,t.region(),t.compartment(),t.kind(),id,t.repository().etag(),name,description);
            flash.addFlashAttribute("message",t.repository()==null?"저장소 생성 완료":"저장소 설정 저장 완료");
        }catch(BusinessException e){flash.addFlashAttribute("message",e.getMessage());}
        return "redirect:/builds?tab=registry&kind="+t.kind()+"&repository="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8)+"&pipeline="+java.net.URLEncoder.encode(t.pipeline(),java.nio.charset.StandardCharsets.UTF_8);
    }

    @GetMapping("/builds/logs") @ResponseBody
    BuildLogs.Page buildLogs(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,
        @RequestParam String pipeline,@RequestParam String run,@RequestParam(defaultValue="") String page,@RequestParam(defaultValue="") String until) {
        var c=SecurityConfig.connection(auth);
        checkScope(selection(c,session),region,compartment);
        if(session.getAttribute("scopeInvalid")!=null) throw Errors.SCOPE.exception();
        return logs.read(c,region,compartment,pipeline,run,page,until);
    }
    record SetupTicket(String region,String compartment,String pipeline,String revision,BuildSetup.Settings settings,String tab) {}
    @SuppressWarnings("unchecked")
    private Map<String,SetupTicket> setupTickets(HttpSession session) {
        var map=(Map<String,SetupTicket>)session.getAttribute("setupTickets");
        if(map==null) { map=new LinkedHashMap<>();session.setAttribute("setupTickets",map); }return map;
    }
    @PostMapping("/builds/setup")
    String setupBuild(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam String action,
        @RequestParam(defaultValue="") String name,@RequestParam(defaultValue="") String description,@RequestParam(defaultValue="") String stage,
        @RequestParam(defaultValue="") String spec,@RequestParam(defaultValue="3600") int timeout,@RequestParam(defaultValue="") String branch,
        @RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String repository,@RequestParam(defaultValue="") String path,
        @RequestParam(defaultValue="") String version,@RequestParam(defaultValue="") String output,@RequestParam(defaultValue="") String artifact,
        @RequestParam(defaultValue="") String buildSource,@RequestParam(defaultValue="") String sourceUrl,@RequestParam(defaultValue="") String image) {
        var c=SecurityConfig.connection(auth);SetupTicket t;
        synchronized(session) { t=setupTickets(session).remove(ticket); }
        if(t==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        checkScope(selection(c,session),t.region(),t.compartment());
        if(session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        try {
            switch(action) {
                case "create-build" -> setup.createBuild(c,t.region(),t.compartment(),t.pipeline(),t.revision(),new BuildSetup.NewBuild(name,buildSource,sourceUrl,branch,spec,image,timeout,stage),ticket);
                case "pipeline" -> setup.rename(c,t.region(),t.compartment(),t.pipeline(),t.settings().etag(),name,description);
                case "stage" -> {
                    var saved=t.settings().stages().stream().filter(v->v.id().equals(stage)).findFirst().orElseThrow(Errors.SCOPE::exception);
                    setup.updateBuild(c,t.region(),t.compartment(),t.pipeline(),stage,saved.etag(),name,spec,timeout,branch);
                }
                case "delivery" -> {
                    var saved=t.settings().deliveries().stream().filter(v->v.id().equals(stage)).findFirst().orElseThrow(Errors.SCOPE::exception);
                    setup.updateDelivery(c,t.region(),t.compartment(),t.pipeline(),stage,saved.etag(),output,artifact);
                }
                case "repository" -> setup.createRepository(c,t.region(),t.compartment(),name,ticket);
                case "destination" -> {
                    var saved=t.settings().deliveries().stream().filter(v->v.id().equals(stage)).findFirst().orElseThrow(Errors.SCOPE::exception);
                    if(saved.mappings().stream().noneMatch(m->m.output().equals(output)))throw Errors.SCOPE.exception();
                    setup.destination(c,t.region(),t.compartment(),t.pipeline(),stage,saved.etag(),output,repository,path,version,ticket);
                }
                case "artifact" -> setup.createArtifact(c,t.region(),t.compartment(),t.pipeline(),name,type,repository,path,version,ticket);
                case "deliver" -> setup.deliver(c,t.region(),t.compartment(),t.pipeline(),t.revision(),stage,output,artifact,ticket);
                default -> throw Errors.BUILD_PARAMETERS.exception();
            }
            flash.addFlashAttribute("message","OCI 설정 요청 완료 · 단계 상태와 저장값을 다시 조회하세요. 실행 중인 빌드에는 적용되지 않습니다.");
        } catch(BusinessException e) { flash.addFlashAttribute("message",e.getMessage()); }
        return "redirect:/builds?tab="+t.tab()+"&pipeline="+java.net.URLEncoder.encode(t.pipeline(),java.nio.charset.StandardCharsets.UTF_8);
    }
    record AdminTicket(String region,String compartment,String tab) {}
    @SuppressWarnings("unchecked")
    private Map<String,AdminTicket> adminTickets(HttpSession session) {
        var map=(Map<String,AdminTicket>)session.getAttribute("adminTickets");
        if(map==null) { map=new LinkedHashMap<>();session.setAttribute("adminTickets",map); }
        return map;
    }
    private String adminTicket(HttpSession session,Selection s,String tab) {
        synchronized(session) {
            var map=adminTickets(session);
            if(map.size()>=32) map.remove(map.keySet().iterator().next());
            var token=UUID.randomUUID().toString();map.put(token,new AdminTicket(s.region(),s.compartment(),tab));return token;
        }
    }
    @PostMapping("/builds/create")
    String createBuildResource(Authentication auth,HttpSession session,RedirectAttributes flash,
        @RequestParam String ticket,@RequestParam String name,@RequestParam(defaultValue="") String description,
        @RequestParam(defaultValue="") String topic,@RequestParam(defaultValue="") String project) {
        var c=SecurityConfig.connection(auth);AdminTicket t;
        synchronized(session) { t=adminTickets(session).remove(ticket); }
        if(t==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        checkScope(selection(c,session),t.region(),t.compartment());
        if(session.getAttribute("scopeInvalid")!=null) throw Errors.SCOPE.exception();
        String destination="/builds?tab="+t.tab();
        try {
            if(t.tab().equals("topics")) {
                String id=administration.createTopic(c,t.region(),t.compartment(),name,description,ticket);
                flash.addFlashAttribute("message","알림 주제 생성 완료 · 알림을 받으려면 구독을 등록하세요.");
                destination="/builds?tab=topics&topic="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);
            } else if(t.tab().equals("projects")) {
                String id=administration.createProject(c,t.region(),t.compartment(),name,description,topic,ticket);
                flash.addFlashAttribute("message","프로젝트 생성 완료 · 실행 전 프로젝트 로그를 활성화하세요.");
                destination="/builds?tab=projects&project="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);
            } else {
                String id=administration.createPipeline(c,t.region(),t.compartment(),project,name,description,ticket);
                flash.addFlashAttribute("message","파이프라인 생성 완료 · 빌드 단계를 등록해야 실행할 수 있습니다.");
                destination="/builds?tab=pipelines&project="+java.net.URLEncoder.encode(project,java.nio.charset.StandardCharsets.UTF_8)+"&pipeline="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch(BusinessException e) { flash.addFlashAttribute("message",e.getMessage()); }
        return "redirect:"+destination;
    }
    record BuildTicket(String region,String compartment,String pipeline,String etag,String revision) {}
    @SuppressWarnings("unchecked")
    private Map<String,BuildTicket> tickets(HttpSession session) {
        var value=(Map<String,BuildTicket>)session.getAttribute("buildTickets");
        if(value==null) { value=new LinkedHashMap<>();session.setAttribute("buildTickets",value); }
        return value;
    }
    @GetMapping("/builds/source-options") @ResponseBody
    Map<String,Object> buildSourceOptions(Authentication auth,HttpSession session,@RequestParam String ticket,@RequestParam String source,
        @RequestParam(defaultValue="") String branch,@RequestParam(defaultValue="") String commit) {
        var c=SecurityConfig.connection(auth);BuildTicket t;
        synchronized(session) { t=tickets(session).get(ticket); }
        if(t==null) throw Errors.CONFLICT.exception();
        checkScope(selection(c,session),t.region(),t.compartment());
        if(session.getAttribute("scopeInvalid")!=null) throw Errors.SCOPE.exception();
        var definition=definitions.read(c,t.region(),t.compartment(),t.pipeline());
        if(!definition.revision().equals(t.revision())) throw Errors.BUILD_CHANGED.exception();
        var chosen=definition.sources().stream().filter(v->v.key().equals(source)).findFirst().orElseThrow(Errors.BUILD_PARAMETERS::exception);
        var branches=definitions.branches(c,t.region(),chosen);
        String requestedBranch=branch.isEmpty()?chosen.branch():branch;
        String selected=branches.stream().anyMatch(v->v.value().equals(requestedBranch))?requestedBranch:branches.isEmpty()?"":branches.getFirst().value();
        var commits=selected.isEmpty()?List.<BuildDefinition.Choice>of():definitions.commits(c,t.region(),chosen,selected);
        if(!commit.isEmpty() && commits.stream().noneMatch(v->v.value().equals(commit))) {
            if(!definitions.validCommit(c,t.region(),chosen,commit))throw Errors.BUILD_PARAMETERS.exception();
            var expanded=new ArrayList<>(commits);expanded.addFirst(new BuildDefinition.Choice(commit,commit.substring(0,12)+" · 저장된 커밋"));commits=expanded;
        }
        return Map.of("branches",branches,"branch",selected,"commits",commits,"external",chosen.repositoryId().isEmpty() && commits.isEmpty());
    }
    @PostMapping("/builds/action")
    String buildAction(Authentication auth,HttpSession session,RedirectAttributes flash,
        @RequestParam String ticket,@RequestParam String action,
        @RequestParam(defaultValue="") String presetName,@RequestParam(defaultValue="") String runName,
        @RequestParam(defaultValue="") String source,@RequestParam(defaultValue="") String branch,@RequestParam(defaultValue="") String commit,
        @RequestParam org.springframework.util.MultiValueMap<String,String> input) {
        var parameterName=input.get("parameterName");var parameterValue=input.get("parameterValue");var parameterDescription=input.get("parameterDescription");
        var ruleName=input.get("ruleName");var ruleType=input.get("ruleType");var ruleRequirement=input.get("ruleRequirement");var ruleChoices=input.get("ruleChoices");
        var c=SecurityConfig.connection(auth);
        BuildTicket t;
        synchronized(session) { t=tickets(session).remove(ticket); }
        if(t==null) throw Errors.CONFLICT.exception();
        String destination="/builds?tab=execute&pipeline="+java.net.URLEncoder.encode(t.pipeline(),java.nio.charset.StandardCharsets.UTF_8);
        try {
            checkScope(selection(c,session),t.region(),t.compartment());
            if(session.getAttribute("scopeInvalid")!=null) throw Errors.SCOPE.exception();
            if(!Set.of("run","defaults","preset","delete","rules").contains(action)) throw Errors.BUILD_PARAMETERS.exception();
            var values=new ArrayList<BuildControl.Parameter>();var names=new HashSet<String>();
            if(parameterName!=null) {
                if(parameterName.size()>100 || parameterValue==null || parameterDescription==null ||
                    parameterName.size()!=parameterValue.size() || parameterName.size()!=parameterDescription.size()) throw Errors.BUILD_PARAMETERS.exception();
                for(int i=0;i<parameterName.size();i++) {
                    String n=parameterName.get(i).trim(),v=parameterValue.get(i),d=parameterDescription.get(i);
                    if(n.isEmpty() && v.isEmpty() && d.isEmpty()) continue;
                    if(!n.matches("[a-zA-Z_][a-zA-Z0-9_]{0,254}") || !names.add(n) || v.length()>4096 || d.length()>1024) throw Errors.BUILD_PARAMETERS.exception();
                    values.add(new BuildControl.Parameter(n,v,d));
                }
            }
            if(runName.length()>255 || presetName.length()>100) throw Errors.BUILD_PARAMETERS.exception();
            var override=new BuildDefinition.SourceOverride(source,branch,commit);
            switch(action) {
                case "preset","delete" -> {
                    if(presetName.isBlank()) throw Errors.BUILD_PARAMETERS.exception();
                    if(action.equals("preset")) presets.save(c,t.region(),t.compartment(),t.pipeline(),new BuildPresetStore.Preset(presetName.trim(),values,runName,override));
                    else presets.delete(c,t.region(),t.compartment(),t.pipeline(),presetName.trim());
                    flash.addFlashAttribute("message",action.equals("preset")?"프리셋 저장 완료":"프리셋 삭제 완료");
                }
                case "rules" -> {
                    var current=control.settings(c,t.region(),t.compartment(),t.pipeline());
                    if(!current.etag().equals(t.etag())) throw Errors.BUILD_CHANGED.exception();
                    inputRules.write(c,t.region(),t.compartment(),t.pipeline(),BuildInputs.rules(current.parameters(),ruleName,ruleType,ruleRequirement,ruleChoices));
                    flash.addFlashAttribute("message","입력 규칙 저장 완료");
                }
                case "defaults" -> {
                    control.defaults(c,t.region(),t.compartment(),t.pipeline(),t.etag(),values);
                    flash.addFlashAttribute("message","OCI 기본값 저장 완료");
                }
                case "run" -> {
                    var current=control.settings(c,t.region(),t.compartment(),t.pipeline());
                    BuildInputs.validate(BuildInputs.fields(current.parameters(),values,inputRules.read(c,t.region(),t.compartment(),t.pipeline())));
                    var currentDefinition=definitions.read(c,t.region(),t.compartment(),t.pipeline());
                    if(!Objects.equals(currentDefinition.revision(),t.revision()))throw Errors.BUILD_CHANGED.exception();
                    var currentSetup=setup.read(c,t.region(),t.compartment(),t.pipeline());
                    var currentSpecs=new LinkedHashMap<String,BuildDefinition.Specification>();
                    for(var item:currentDefinition.stages()) if(!item.spec().isEmpty()) {
                        var primary=item.sources().stream().filter(BuildDefinition.Source::primary).findFirst().orElse(item.sources().isEmpty()?null:item.sources().getFirst());
                        if(primary!=null) {
                            if(primary.key().equals(override.source()))primary=new BuildDefinition.Source(primary.key(),primary.name(),primary.provider(),primary.repositoryId(),primary.url(),override.commit().isEmpty()?override.branch():override.commit(),true);
                            currentSpecs.put(item.id(),definitions.specification(c,t.region(),primary,item.spec()));
                        }
                    }
                    BuildChecks.validate(currentDefinition,currentSetup,currentSpecs,input.getFirst("purpose"));
                    String run=control.start(c,t.region(),t.compartment(),t.pipeline(),t.etag(),runName,values,ticket,override,t.revision());
                    destination="/builds?pipeline="+java.net.URLEncoder.encode(t.pipeline(),java.nio.charset.StandardCharsets.UTF_8)+"&run="+java.net.URLEncoder.encode(run,java.nio.charset.StandardCharsets.UTF_8);
                    flash.addFlashAttribute("message","빌드 실행 요청 완료");
                }
                default -> throw Errors.BUILD_PARAMETERS.exception();
            }
        } catch(BusinessException | IllegalStateException e) { flash.addFlashAttribute("message",e.getMessage()); }
        return "redirect:"+destination;
    }
    @PostMapping("/catalog/refresh") String refresh(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="selected") String mode,@RequestParam(defaultValue="/") String back) {
        var c=SecurityConfig.connection(auth); var s=selection(c,session);
        checkScope(s,region,compartment);
        if (!s.mode().equals(mode)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
        var reports=new LinkedHashMap<String,Report>();int failed=0;
        for (var target:targets(s)) {
            long started=System.nanoTime();
            var view=service.refresh(c,s.region(),target.id());
            reports.put(service.scope(c,s.region(),target.id()),new Report(view.error(),(System.nanoTime()-started)/1_000_000));
            if (view.error()!=null) failed++;
        }
        session.setAttribute("scopeReports",reports);
        flash.addFlashAttribute("message",failed==0?"갱신 완료":"갱신 실패 "+failed+"개 구획 · 이전 캐시 유지");
        return "redirect:"+(back.equals("/sources")?"/sources":"/");
    }
    @PostMapping("/catalog/refresh-one") @ResponseBody
    ScopeResult refreshOne(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,
        @RequestParam String mode,@RequestParam String target) {
        var c=SecurityConfig.connection(auth);var s=selection(c,session);checkScope(s,region,compartment);
        if (!s.mode().equals(mode)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
        var choice=targets(s).stream().filter(t -> t.id().equals(target)).findFirst().orElseThrow(Errors.INVALID::exception);
        long started=System.nanoTime();var view=service.refresh(c,region,target);
        long elapsed=(System.nanoTime()-started)/1_000_000;
        synchronized(session) {
            @SuppressWarnings("unchecked") var previous=(Map<String,Report>)session.getAttribute("scopeReports");
            var reports=new LinkedHashMap<String,Report>();if(previous!=null) reports.putAll(previous);
            reports.put(service.scope(c,region,target),new Report(view.error(),elapsed));session.setAttribute("scopeReports",reports);
        }
        return new ScopeResult(choice,view.snapshot()==null?null:view.snapshot().updatedAt(),view.error(),elapsed);
    }
    @PostMapping("/catalog/scopes") String reload(HttpSession session) {
        for (String key:List.of("regions","compartments","region","compartment","scopeMode","scopeReports","scopeLoaded","scopeInvalid")) session.removeAttribute(key);
        return "redirect:/";
    }
    @PostMapping("/sources") String source(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam String provider,@RequestParam String name,@RequestParam(defaultValue="") String url,@RequestParam String branch,@RequestParam(defaultValue="") String resourceId,RedirectAttributes flash) {
        var c=SecurityConfig.connection(auth);var s=selection(c,session);
        checkScope(s,region,compartment);
        try {
            String sourceCompartment=s.compartment();
            if (provider.equals("OCI")) sourceCompartment=rows(c,s).stream().filter(r -> r.resource().kind().equals("REPOSITORY") && r.resource().id().equals(resourceId))
                .map(r -> r.compartment().id()).findFirst().orElseThrow(Errors.MISSING::exception);
            service.register(c,s.region(),sourceCompartment,provider,name,url,branch,resourceId); flash.addFlashAttribute("message","등록 완료 · 연결 검증 전"); }
        catch (BusinessException e) { flash.addFlashAttribute("message",e.getMessage()); }
        catch (IllegalStateException e) { flash.addFlashAttribute("message",e.getMessage()); }
        return "redirect:/sources";
    }
    @PostMapping("/sources/{id}/remove") String remove(@PathVariable String id,@RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="") String sourceCompartment,Authentication auth,HttpSession session) {
        var c=SecurityConfig.connection(auth);var s=selection(c,session);checkScope(s,region,compartment);String origin=sourceCompartment.isEmpty()?s.compartment():sourceCompartment;
        if (targets(s).stream().noneMatch(t -> t.id().equals(origin))) throw Errors.INVALID.exception();
        service.remove(c,s.region(),origin,id);return "redirect:/sources";
    }
    @GetMapping("/guides") String guides(Model model) { model.addAttribute("guides",guides.all());return "guides"; }
    @ExceptionHandler(BusinessException.class)
    org.springframework.web.servlet.ModelAndView invalid(BusinessException e) {
        var result=new org.springframework.web.servlet.ModelAndView("error");
        result.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        result.addObject("message",e.getMessage());return result;
    }
    private void checkScope(Selection selection,String region,String compartment) {
        if (!selection.region().equals(region) || !selection.compartment().equals(compartment)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"조회 범위 변경 · 새로 고침");
    }
    private static String quote(String value) { return "'"+value.replace("'","'\"'\"'")+"'"; }
    private String cli(Connection c,Selection s) {
        var commands=new ArrayList<String>();
        for (var target:targets(s)) {
            String flags=" --profile "+quote(c.profile())+" --region "+quote(s.region())+" --compartment-id "+quote(target.id())+" --all";
            commands.add("oci devops repository list"+flags);
        }
        return String.join("\n",commands);
    }
}
