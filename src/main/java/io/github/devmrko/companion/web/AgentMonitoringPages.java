package io.github.devmrko.companion.web;

import io.github.devmrko.companion.port.AgentMonitoring;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller @Profile("!legacy")
public class AgentMonitoringPages {
    private final CatalogPages catalog;
    private final AgentMonitoring monitoring;
    public AgentMonitoringPages(CatalogPages catalog, AgentMonitoring monitoring) {
        this.catalog=catalog; this.monitoring=monitoring;
    }
    @GetMapping("/agents") String page(Authentication auth, HttpServletRequest request, Model model) {
        var c=SecurityConfig.connection(auth);
        catalog.navigation(auth, request, model);
        var scope=catalog.selection(c,request.getSession());
        model.addAttribute("connection",c);
        model.addAttribute("scopeBack","/agents");
        if(request.getSession().getAttribute("scopeInvalid")==null)
            model.addAttribute("agent",monitoring.snapshot(c,scope.region(),scope.compartment()));
        return "agent-monitoring";
    }
}
