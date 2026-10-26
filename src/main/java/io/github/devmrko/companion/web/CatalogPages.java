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
    public CatalogPages(CloudConnection cloud,ResourceCatalog catalog,CatalogService service,SecurityContextRepository contexts,GuideCatalog guides) {
        this.cloud=cloud; this.catalog=catalog; this.service=service; this.contexts=contexts; this.guides=guides;
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

    public record Selection(List<Choice> regions,List<Choice> compartments,String region,String compartment,int interval) {}
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
        return new Selection(regions,compartments,region,compartment,interval==null?0:interval);
    }
    @GetMapping({"/","/sources"}) String page(Authentication auth,HttpSession session,HttpServletRequest request,Model model) {
        var c=SecurityConfig.connection(auth);
        boolean sources=request.getRequestURI().equals("/sources");
        model.addAttribute("sourcePage",sources);
        try {
            var s=selection(c,session);
            model.addAttribute("selection",s);
            var view=service.read(c,s.region(),s.compartment());
            model.addAttribute("snapshot",view.snapshot()); model.addAttribute("cacheError",view.error());
            model.addAttribute("sources",service.sources(c,s.region(),s.compartment()));
            model.addAttribute("cli",cli(c,s));
        } catch (BusinessException e) { model.addAttribute("message","범위 조회 실패 · IAM 권한과 연결 확인"); }
          catch (IllegalStateException e) { model.addAttribute("message",e.getMessage()); }
        return "catalog";
    }
    @PostMapping("/scope") String scope(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="0") int interval) {
        var s=selection(SecurityConfig.connection(auth),session);
        if (s.regions().stream().noneMatch(r->r.id().equals(region)) || s.compartments().stream().noneMatch(r->r.id().equals(compartment)) || !Set.of(0,60,300,900).contains(interval)) throw Errors.INVALID.exception();
        session.setAttribute("region",region); session.setAttribute("compartment",compartment); session.setAttribute("interval",interval);
        return "redirect:/";
    }
    @PostMapping("/catalog/refresh") String refresh(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="/") String back) {
        var c=SecurityConfig.connection(auth); var s=selection(c,session);
        checkScope(s,region,compartment);
        var view=service.refresh(c,s.region(),s.compartment());
        flash.addFlashAttribute("message",view.error()==null?"갱신 완료":view.error());
        return "redirect:"+(back.equals("/sources")?"/sources":"/");
    }
    @PostMapping("/catalog/scopes") String reload(HttpSession session) {
        for (String key:List.of("regions","compartments","region","compartment")) session.removeAttribute(key);
        return "redirect:/";
    }
    @PostMapping("/sources") String source(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam String provider,@RequestParam String name,@RequestParam(defaultValue="") String url,@RequestParam String branch,@RequestParam(defaultValue="") String resourceId,RedirectAttributes flash) {
        var c=SecurityConfig.connection(auth);var s=selection(c,session);
        checkScope(s,region,compartment);
        try { service.register(c,s.region(),s.compartment(),provider,name,url,branch,resourceId); flash.addFlashAttribute("message","등록 완료 · 연결 검증 전"); }
        catch (BusinessException e) { flash.addFlashAttribute("message",e.getMessage()); }
        catch (IllegalStateException e) { flash.addFlashAttribute("message",e.getMessage()); }
        return "redirect:/sources";
    }
    @PostMapping("/sources/{id}/remove") String remove(@PathVariable String id,@RequestParam String region,@RequestParam String compartment,Authentication auth,HttpSession session) {
        var c=SecurityConfig.connection(auth);var s=selection(c,session);checkScope(s,region,compartment);service.remove(c,s.region(),s.compartment(),id);return "redirect:/sources";
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
        String flags=" --profile "+quote(c.profile())+" --region "+quote(s.region())+" --compartment-id "+quote(s.compartment())+" --all";
        return "oci devops project list"+flags+"\noci devops repository list"+flags+"\noci devops build-pipeline list"+flags+"\noci devops deploy-pipeline list"+flags;
    }
}
