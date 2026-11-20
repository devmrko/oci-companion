package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller @Profile("!legacy")
public class ReadinessPages {
 private final CatalogPages catalog;private final BuildHistory builds;private final BuildDefinition definitions;private final BuildSetup setup;
 public ReadinessPages(CatalogPages catalog,BuildHistory builds,BuildDefinition definitions,BuildSetup setup){this.catalog=catalog;this.builds=builds;this.definitions=definitions;this.setup=setup;}
 @GetMapping("/readiness") String page(Authentication auth,HttpServletRequest req,Model model,@RequestParam(defaultValue="") String pipeline){
  catalog.navigation(auth,req,model);model.addAttribute("scopeBack","/readiness");model.addAttribute("pipeline",pipeline);
  try{
   if(req.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
   var c=SecurityConfig.connection(auth);var s=catalog.selection(c,req.getSession());var list=builds.pipelines(c,s.region(),s.compartment());model.addAttribute("pipelines",list);
   if(pipeline.isBlank())return "readiness";
   if(list.stream().noneMatch(p->p.id().equals(pipeline)))throw Errors.SCOPE.exception();
   var definition=definitions.read(c,s.region(),s.compartment(),pipeline);var specs=new LinkedHashMap<String,BuildDefinition.Specification>();
   for(var stage:definition.stages())if(!stage.spec().isBlank()){
    var source=stage.sources().stream().filter(BuildDefinition.Source::primary).findFirst();if(source.isEmpty()&&stage.sources().size()==1)source=Optional.of(stage.sources().getFirst());
    if(source.isPresent())try{specs.put(stage.id(),definitions.specification(c,s.region(),source.get(),stage.spec()));}catch(BusinessException e){/* unknown is displayed below */}
   }
   BuildSetup.Settings settings=null;try{settings=setup.read(c,s.region(),s.compartment(),pipeline);}catch(BusinessException e){/* keep other evidence */}
   model.addAttribute("preparation",Readiness.prepare(definition,settings,specs,pipeline));model.addAttribute("selectedName",definition.name());
  }catch(BusinessException|IllegalStateException e){model.addAttribute("preparation",Readiness.unknown("조회 실패 · 연결·범위·권한 확인"));}
  return "readiness";
 }
}
