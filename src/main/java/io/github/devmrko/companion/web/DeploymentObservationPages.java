package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.DeploymentObservation;
import io.github.devmrko.companion.adapter.AgentConnectionRegistry;
import io.github.devmrko.companion.adapter.OkeConnectionStore;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller @Profile("!legacy")
public class DeploymentObservationPages {
 private final CatalogPages catalog;private final DeploymentObservation observation;private final AgentConnectionRegistry registry;private final OkeConnectionStore store;
 public DeploymentObservationPages(CatalogPages catalog,DeploymentObservation observation,AgentConnectionRegistry registry,OkeConnectionStore store){this.catalog=catalog;this.observation=observation;this.registry=registry;this.store=store;}
 public record App(String id,String name,String namespace,String deployment){}
 public record Group(DeploymentObservation.Target target,List<App> apps){}
 @GetMapping("/deployments/observe") String page(Authentication auth,HttpServletRequest req,Model model,@RequestParam String pipeline,@RequestParam String run){
  var c=SecurityConfig.connection(auth);catalog.navigation(auth,req,model);var s=catalog.selection(c,req.getSession());model.addAttribute("scopeBack","/deployments/runs");model.addAttribute("pipelineId",pipeline);model.addAttribute("runId",run);
  try{
   if(c.demo()||req.getSession().getAttribute("scopeInvalid")!=null)throw new IllegalArgumentException("실제 OCI 연결과 리전·구획을 선택하세요.");
   var context=observation.read(c,s.region(),s.compartment(),pipeline,run);model.addAttribute("context",context);var all=registry.list(c,s.region(),s.compartment());var groups=new ArrayList<Group>();
   for(var t:context.targets()){
    var apps=new ArrayList<App>();for(var a:all)if("oke-workload-v1".equals(a.adapter())&&a.dependencies().stream().anyMatch(x->"OKE".equals(x.type())&&t.cluster().equals(x.ocid()))){var binding=store.get(c,s.region(),s.compartment(),a.id());if(binding!=null&&t.cluster().equals(binding.clusterId()))apps.add(new App(a.id(),a.name(),binding.namespace(),binding.deployment()));}
    groups.add(new Group(t,List.copyOf(apps)));
   }
   model.addAttribute("groups",groups);
  }catch(IllegalArgumentException e){model.addAttribute("observationError",e.getMessage());}return "deployment-observation";
 }
}
