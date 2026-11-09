package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentRuns;
import io.github.devmrko.companion.port.BuildHandoff;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @Profile("!legacy")
public class DeploymentRunPages {
    private final io.github.devmrko.companion.port.DeploymentImages images;private final CatalogPages catalog;private final DeploymentRuns runs;private final BuildHandoff handoff;private final io.github.devmrko.companion.port.DeploymentLogs logs;
    public DeploymentRunPages(CatalogPages catalog,DeploymentRuns runs,BuildHandoff handoff,io.github.devmrko.companion.port.DeploymentLogs logs,io.github.devmrko.companion.port.DeploymentImages images){this.images=images;this.catalog=catalog;this.runs=runs;this.handoff=handoff;this.logs=logs;}
    record Ticket(Connection connection,String region,String compartment,String pipeline,String fingerprint,String sourcePipeline,String sourceRun,String sourceFingerprint,Instant expires) {}
    @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("deploymentRunTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("deploymentRunTickets",m);}return m;}
    @GetMapping("/deployments/runs") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String pipeline,@RequestParam(defaultValue="") String run,@RequestParam(defaultValue="") String sourcePipeline,@RequestParam(defaultValue="") String sourceRun,@RequestParam(defaultValue="") String historyPage,@RequestParam(defaultValue="") String logPage,@RequestParam(defaultValue="") String logUntil){
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var scope=catalog.selection(c,request.getSession());model.addAttribute("scopeBack","/deployments/runs");model.addAttribute("pipelineId",pipeline);model.addAttribute("preflightRequested",!pipeline.isBlank() && run.isBlank());model.addAttribute("sourcePipeline",sourcePipeline);model.addAttribute("sourceRun",sourceRun);
        if(request.getSession().getAttribute("scopeInvalid")!=null)return "deployment-runs";
        try{
            if(sourcePipeline.isBlank()!=sourceRun.isBlank())throw Errors.HANDOFF.exception();
            model.addAttribute("pipelines",runs.pipelines(c,scope.region(),scope.compartment()));
            if(!pipeline.isBlank())model.addAttribute("history",runs.history(c,scope.region(),scope.compartment(),pipeline,historyPage));
            if(!run.isBlank()){model.addAttribute("run",runs.run(c,scope.region(),scope.compartment(),pipeline,run));model.addAttribute("deploymentLogs",logs.read(c,scope.region(),scope.compartment(),pipeline,run,logPage,logUntil));}
            else if(!pipeline.isBlank()){
                var plan=runs.plan(c,scope.region(),scope.compartment(),pipeline);
                var proof=sourceRun.isBlank()?null:handoff.verify(c,scope.region(),scope.compartment(),sourcePipeline,sourceRun,pipeline);
                model.addAttribute("plan",plan);model.addAttribute("buildProof",proof);
                var checks=images.inspect(c,scope.region(),scope.compartment(),plan.images());model.addAttribute("imageChecks",checks);
                if(checks.stream().anyMatch(x->x.state()==io.github.devmrko.companion.port.DeploymentImages.State.BLOCKED))throw Errors.DEPLOYMENT_IMAGE.exception();
                synchronized(request.getSession()){
                    var map=tickets(request.getSession());map.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(map.size()>=32)map.remove(map.keySet().iterator().next());
                    var token=UUID.randomUUID().toString();map.put(token,new Ticket(c,scope.region(),scope.compartment(),pipeline,plan.fingerprint(),sourcePipeline,sourceRun,proof==null?"":proof.fingerprint(),Instant.now().plusSeconds(600)));model.addAttribute("ticket",token);
                }
            }
        }catch(BusinessException e){model.addAttribute("deploymentError",e.getMessage());}
        return "deployment-runs";
    }
    @PostMapping("/deployments/runs") String start(Authentication auth,HttpSession session,@RequestParam String ticket){
        Ticket t;synchronized(session){t=tickets(session).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();
        var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);
        if(!c.equals(t.connection())||session.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();
        if(!t.sourceRun().isBlank()&&!handoff.verify(c,t.region(),t.compartment(),t.sourcePipeline(),t.sourceRun(),t.pipeline()).fingerprint().equals(t.sourceFingerprint()))throw Errors.BUILD_CHANGED.exception();
        var current=runs.plan(c,t.region(),t.compartment(),t.pipeline());
        if(!current.fingerprint().equals(t.fingerprint()))throw Errors.BUILD_CHANGED.exception();
        if(images.inspect(c,t.region(),t.compartment(),current.images()).stream().anyMatch(x->x.state()==io.github.devmrko.companion.port.DeploymentImages.State.BLOCKED))throw Errors.DEPLOYMENT_IMAGE.exception();
        var id=runs.start(c,t.region(),t.compartment(),t.pipeline(),t.fingerprint(),ticket);
        return "redirect:/deployments/runs?pipeline="+encode(t.pipeline())+"&run="+encode(id)
            +(t.sourceRun().isBlank()?"":"&sourcePipeline="+encode(t.sourcePipeline())+"&sourceRun="+encode(t.sourceRun()));
    }
    private static String encode(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
 @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
