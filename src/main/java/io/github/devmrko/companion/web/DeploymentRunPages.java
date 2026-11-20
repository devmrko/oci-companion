package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.DeploymentRuns;
import io.github.devmrko.companion.port.DeploymentOperations;
import io.github.devmrko.companion.port.BuildHandoff;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @Profile("!legacy")
public class DeploymentRunPages {
    private final CatalogPages catalog;private final DeploymentOperations operations;
    public DeploymentRunPages(CatalogPages catalog,DeploymentOperations operations){this.catalog=catalog;this.operations=operations;}
    private DeploymentOperations.Context context(Authentication auth,HttpSession session){
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        return new DeploymentOperations.Context(c,s.region(),s.compartment(),session.getId());
    }
    @GetMapping("/deployments/runs") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String pipeline,@RequestParam(defaultValue="") String run,@RequestParam(defaultValue="") String sourcePipeline,@RequestParam(defaultValue="") String sourceRun,@RequestParam(defaultValue="") String historyPage,@RequestParam(defaultValue="") String logPage,@RequestParam(defaultValue="") String logUntil){
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var scope=catalog.selection(c,request.getSession());model.addAttribute("scopeBack","/deployments/runs");model.addAttribute("pipelineId",pipeline);model.addAttribute("preflightRequested",!pipeline.isBlank() && run.isBlank());model.addAttribute("sourcePipeline",sourcePipeline);model.addAttribute("sourceRun",sourceRun);
        if(request.getSession().getAttribute("scopeInvalid")!=null)return "deployment-runs";
        try{
            var operationContext=context(auth,request.getSession());
            if(sourcePipeline.isBlank()!=sourceRun.isBlank())throw Errors.HANDOFF.exception();
            if(!sourceRun.isBlank()&&run.isBlank())model.addAttribute("sourceImages",operations.sourceImages(operationContext,new DeploymentOperations.Target(pipeline,sourcePipeline,sourceRun)));
            model.addAttribute("pipelines",operations.pipelines(operationContext));
            if(!pipeline.isBlank())model.addAttribute("history",operations.history(operationContext,pipeline,historyPage));
            if(!run.isBlank()){model.addAttribute("run",operations.status(operationContext,pipeline,run));model.addAttribute("deploymentLogs",operations.logs(operationContext,pipeline,run,logPage,logUntil));}
            else if(!pipeline.isBlank()){
                var prepared=operations.prepare(operationContext,new DeploymentOperations.Target(pipeline,sourcePipeline,sourceRun));
                var assessment=prepared.assessment();
                model.addAttribute("plan",assessment.plan());model.addAttribute("buildProof",assessment.proof());
                model.addAttribute("imageChecks",assessment.images());model.addAttribute("architectureChecks",assessment.architectures());
                model.addAttribute("operationAssessment",assessment);
                if(!prepared.id().isBlank())model.addAttribute("ticket",prepared.id());
                else if(!assessment.issues().isEmpty())model.addAttribute("deploymentError",assessment.issues().getFirst().message());
            }
        }catch(BusinessException e){model.addAttribute("deploymentError",e.getMessage());}
        if(!pipeline.isBlank()&&run.isBlank()){
            @SuppressWarnings("unchecked") var imageList=(List<io.github.devmrko.companion.port.DeploymentImages.Check>)model.getAttribute("imageChecks");
            @SuppressWarnings("unchecked") var architectureList=(List<io.github.devmrko.companion.port.DeploymentImages.Check>)model.getAttribute("architectureChecks");
            model.addAttribute("deploymentPreparation",io.github.devmrko.companion.service.Readiness.deployment((DeploymentRuns.Plan)model.getAttribute("plan"),(BuildHandoff.Proof)model.getAttribute("buildProof"),!sourceRun.isBlank(),imageList,architectureList));
        }
        return "deployment-runs";
    }
    @PostMapping("/deployments/runs") String start(Authentication auth,HttpSession session,@RequestParam String ticket){
        var result=operations.execute(context(auth,session),ticket);var target=result.target();
        return "redirect:/deployments/runs?pipeline="+encode(target.pipeline())+"&run="+encode(result.id())
            +(target.sourceRun().isBlank()?"":"&sourcePipeline="+encode(target.sourcePipeline())+"&sourceRun="+encode(target.sourceRun()));
    }
    private static String encode(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
 @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
