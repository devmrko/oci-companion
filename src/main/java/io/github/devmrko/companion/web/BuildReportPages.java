package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.BuildHistory;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!legacy")
public class BuildReportPages {
    private final BuildHistory builds;private final CatalogPages catalog;
    public BuildReportPages(BuildHistory builds,CatalogPages catalog){this.builds=builds;this.catalog=catalog;}
    @GetMapping("/builds/reports") TestReports.Summary reports(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam String pipeline,@RequestParam String run,@RequestParam String artifact) {
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);
        if(session.getAttribute("scopeInvalid")!=null||!region.equals(s.region())||!compartment.equals(s.compartment()))throw Errors.SCOPE.exception();
        return builds.reports(c,region,compartment,pipeline,run,artifact);
    }
}
