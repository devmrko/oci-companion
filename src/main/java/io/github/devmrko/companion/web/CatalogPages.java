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
    public CatalogPages(CloudConnection cloud,ResourceCatalog catalog,CatalogService service,SecurityContextRepository contexts,GuideCatalog guides,BuildHistory builds) {
        this.cloud=cloud; this.catalog=catalog; this.service=service; this.contexts=contexts; this.guides=guides;this.builds=builds;
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
    private Selection selection(Connection c,HttpSession session) {
        var regions=(List<Choice>)session.getAttribute("regions");
        var compartments=(List<Choice>)session.getAttribute("compartments");
        if (regions==null || compartments==null) {
            regions=catalog.regions(c); compartments=catalog.compartments(c);
            if (regions.isEmpty() || compartments.isEmpty()) throw Errors.MISSING.exception();
            session.setAttribute("regions",regions); session.setAttribute("compartments",compartments);
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
    @PostMapping("/scope") String scope(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="0") int interval,@RequestParam(defaultValue="selected") String mode,@RequestParam(defaultValue="/") String back) {
        var s=selection(SecurityConfig.connection(auth),session);
        if (s.regions().stream().noneMatch(r->r.id().equals(region)) || s.compartments().stream().noneMatch(r->r.id().equals(compartment)) || !Set.of(0,60,300,900).contains(interval)) throw Errors.INVALID.exception();
        CompartmentScopes.targets(s.compartments(),compartment,mode);
        session.setAttribute("region",region); session.setAttribute("compartment",compartment); session.setAttribute("interval",mode.equals("selected")?interval:0);session.setAttribute("scopeMode",mode);
        return "redirect:"+(Set.of("/sources","/builds").contains(back)?back:"/");
    }
    @GetMapping("/builds") String builds(Authentication auth,HttpSession session,Model model,
        @RequestParam(defaultValue="") String pipeline,@RequestParam(defaultValue="") String run) {
        var c=SecurityConfig.connection(auth);
        model.addAttribute("pipelines",List.of());model.addAttribute("runs",List.of());
        try {
            var s=selection(c,session);model.addAttribute("selection",s);
            var list=builds.pipelines(c,s.region(),s.compartment());model.addAttribute("pipelines",list);
            String selected=pipeline.isEmpty() && !list.isEmpty()?list.getFirst().id():pipeline;
            model.addAttribute("pipeline",selected);
            model.addAttribute("buildCli","oci devops build-pipeline list --profile "+quote(c.profile())+" --region "+quote(s.region())+" --compartment-id "+quote(s.compartment())+" --all");
            if(!selected.isEmpty()) {
                if(list.stream().noneMatch(p->p.id().equals(selected))) throw Errors.MISSING.exception();
                var runs=builds.runs(c,s.region(),s.compartment(),selected);model.addAttribute("runs",runs);
                String selectedRun=run.isEmpty() && !runs.isEmpty()?runs.getFirst().id():run;
                if(!selectedRun.isEmpty()) {
                    if(runs.stream().noneMatch(r->r.id().equals(selectedRun))) throw Errors.MISSING.exception();
                    model.addAttribute("detail",builds.detail(c,s.region(),s.compartment(),selected,selectedRun));
                }
            }
        } catch(BusinessException e) { model.addAttribute("message",e.getMessage()); }
        return "builds";
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
        for (String key:List.of("regions","compartments","region","compartment","scopeMode","scopeReports")) session.removeAttribute(key);
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
