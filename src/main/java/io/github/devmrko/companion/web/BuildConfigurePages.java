package io.github.devmrko.companion.web;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller @Profile("!legacy")
public class BuildConfigurePages {
    private final CatalogPages catalog;private final CatalogService sources;
    public BuildConfigurePages(CatalogPages catalog,CatalogService sources){this.catalog=catalog;this.sources=sources;}
    @GetMapping("/builds/configure") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String registeredSource){var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var s=catalog.selection(c,request.getSession());model.addAttribute("selection",s);model.addAttribute("connection",c);model.addAttribute("scopeBack","/builds/configure");model.addAttribute("tab","recipes");model.addAttribute("registeredSource",registeredSource);model.addAttribute("specSources",sources.sources(c,s.region(),s.compartment()));if(!registeredSource.isEmpty())sources.sources(c,s.region(),s.compartment()).stream().filter(v->v.id().equals(registeredSource)).findFirst().ifPresent(v->model.addAttribute("chosenSource",v));return "build-configure";}
}
