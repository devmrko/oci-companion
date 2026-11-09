package io.github.devmrko.companion.web;

import io.github.devmrko.companion.domain.Models.*;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@org.springframework.context.annotation.Profile("legacy")
public class Pages {
    private final CloudConnection cloud;
    private final WorkspaceService service;
    private final List<BuildRecipe> recipes;
    private final GuideCatalog guides;
    private final SecurityContextRepository contexts;
    public Pages(CloudConnection cloud,WorkspaceService service,List<BuildRecipe> recipes,GuideCatalog guides,SecurityContextRepository contexts) {
        this.cloud=cloud; this.service=service; this.recipes=recipes; this.guides=guides; this.contexts=contexts;
    }
    @ModelAttribute("connection") Connection connection(Authentication auth) { return auth!=null && auth.getPrincipal() instanceof Connection c?c:null; }
    @ModelAttribute("legacyView") boolean legacyView() { return true; }
    @ModelAttribute("recipes") List<BuildRecipe> recipes() { return recipes; }
    @GetMapping("/login") String login(Model model) { model.addAttribute("profiles",cloud.profiles()); return "login"; }
    @PostMapping("/connect") String connect(@RequestParam String profile,HttpServletRequest req,HttpServletResponse res,RedirectAttributes flash) {
        try {
            var c=cloud.connect(profile);
            if (req.getSession(false)!=null) req.changeSessionId(); else req.getSession(true);
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(c,null,List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
            SecurityContextHolder.setContext(context); contexts.saveContext(context,req,res);
            return "redirect:/";
        } catch (BusinessException e) { flash.addFlashAttribute("message",Errors.CONFIG.safeMessage()); return "redirect:/login"; }
    }
    @GetMapping("/") String home(Authentication auth,Model model) {
        var c=SecurityConfig.connection(auth);
        model.addAttribute("workspaces",service.list(c)); return "home";
    }
    @GetMapping("/projects/new") String newProject(Model model) {
        model.addAttribute("workspace",null); return "edit";
    }
    @GetMapping("/projects/{id}/edit") String edit(@PathVariable String id,Authentication auth,Model model) {
        model.addAttribute("workspace",service.get(SecurityConfig.connection(auth),id)); return "edit";
    }
    @PostMapping("/projects") String save(Authentication auth,@RequestParam(defaultValue="") String id,@RequestParam String name,
        @RequestParam String repositoryUrl,@RequestParam String branch,@RequestParam String recipe,@RequestParam String compartmentId,
        @RequestParam(defaultValue="") String buildPipelineId,@RequestParam(defaultValue="") String deployPipelineId) {
        var w=service.save(SecurityConfig.connection(auth),id,name,repositoryUrl,branch,recipe,compartmentId,buildPipelineId,deployPipelineId);
        return "redirect:/projects/"+w.id();
    }
    @GetMapping("/projects/{id}") String project(@PathVariable String id,Authentication auth,Model model) {
        var c=SecurityConfig.connection(auth);
        model.addAttribute("workspace",service.get(c,id));
        model.addAttribute("runs",service.runs(c,id));
        model.addAttribute("buildToken",UUID.randomUUID().toString());
        model.addAttribute("deployToken",UUID.randomUUID().toString());
        return "project";
    }
    @GetMapping("/projects/{id}/pipelines") String pipelines(@PathVariable String id,Authentication auth,Model model) {
        var c=SecurityConfig.connection(auth);
        model.addAttribute("workspace",service.get(c,id));
        model.addAttribute("pipelines",service.pipelines(c,id)); return "pipelines";
    }
    @PostMapping("/projects/{id}/run") String run(@PathVariable String id,@RequestParam String kind,@RequestParam String token,@RequestParam String expectedPipeline,
                                                @RequestParam(defaultValue="false") boolean acknowledged,Authentication auth,RedirectAttributes flash) {
        if (!acknowledged) throw Errors.INVALID.exception();
        try { service.start(SecurityConfig.connection(auth),id,kind,token,expectedPipeline); }
        catch (BusinessException e) { flash.addFlashAttribute("message",e.getMessage()); }
        return "redirect:/projects/"+id;
    }
    @PostMapping("/projects/{id}/runs/{runId}/refresh") String refresh(@PathVariable String id,@PathVariable String runId,Authentication auth,RedirectAttributes flash) {
        try { service.refresh(SecurityConfig.connection(auth),id,runId); }
        catch (BusinessException e) { flash.addFlashAttribute("message",e.getMessage()); }
        return "redirect:/projects/"+id;
    }
    @GetMapping("/projects/{id}/build-spec") ResponseEntity<String> spec(@PathVariable String id,Authentication auth) {
        var w=service.get(SecurityConfig.connection(auth),id);
        var recipe=recipes.stream().filter(r -> r.id().equals(w.recipe())).findFirst().orElseThrow(Errors.INVALID::exception);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=build_spec.yaml")
            .contentType(MediaType.parseMediaType("text/yaml;charset=UTF-8")).body(recipe.specification());
    }
    @GetMapping("/guides") String guides(Model model) { model.addAttribute("guides",guides.all()); return "guides"; }
}
