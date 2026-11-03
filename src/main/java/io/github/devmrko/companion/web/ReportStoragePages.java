package io.github.devmrko.companion.web;

import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!legacy")
public class ReportStoragePages {
    private final ReportStorage storage;private final CatalogPages catalog;private final BuildHistory builds;
    public ReportStoragePages(ReportStorage storage,CatalogPages catalog,BuildHistory builds){this.storage=storage;this.catalog=catalog;this.builds=builds;}
    private CatalogPages.Selection scope(Authentication auth,HttpSession session,String region,String compartment){
        var s=catalog.selection(SecurityConfig.connection(auth),session);
        if(session.getAttribute("scopeInvalid")!=null||!s.region().equals(region)||!s.compartment().equals(compartment))throw Errors.SCOPE.exception();return s;
    }
    @GetMapping("/builds/report-storage") ReportStorage.Options options(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment){
        scope(auth,session,region,compartment);return storage.options(SecurityConfig.connection(auth),region,compartment);
    }
    @PostMapping("/builds/report-storage/buckets") void create(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam String name){
        scope(auth,session,region,compartment);storage.createBucket(SecurityConfig.connection(auth),region,compartment,name);
    }
    private String runPrefix(Authentication auth,HttpSession session,String region,String compartment,String pipeline,String run,String prefix){
        scope(auth,session,region,compartment);builds.detail(SecurityConfig.connection(auth),region,compartment,pipeline,run);
        ReportSettings.prefix(prefix);return prefix+"/"+pipeline+"/"+run+"/";
    }
    @GetMapping("/builds/report-storage/objects") List<String> objects(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam String pipeline,@RequestParam String run,@RequestParam String bucket,@RequestParam String prefix){
        String path=runPrefix(auth,session,region,compartment,pipeline,run,prefix);return storage.objects(SecurityConfig.connection(auth),region,compartment,bucket,path);
    }
    @GetMapping("/builds/report-storage/report") TestReports.Summary read(Authentication auth,HttpSession session,@RequestParam String region,@RequestParam String compartment,@RequestParam String pipeline,@RequestParam String run,@RequestParam String bucket,@RequestParam String prefix,@RequestParam String object){
        String path=runPrefix(auth,session,region,compartment,pipeline,run,prefix);
        if(!object.startsWith(path)||!object.endsWith("/test-reports.tar.gz"))throw Errors.SCOPE.exception();
        return storage.read(SecurityConfig.connection(auth),region,compartment,bucket,object);
    }
}
