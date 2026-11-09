package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @Profile("!legacy")
public class BuildAutomationPages {
    private final CatalogPages catalog;private final BuildHistory builds;private final BuildDefinition definitions;private final BuildAutomation automation;
    public BuildAutomationPages(CatalogPages catalog,BuildHistory builds,BuildDefinition definitions,BuildAutomation automation){this.catalog=catalog;this.builds=builds;this.definitions=definitions;this.automation=automation;}
    @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView invalid(BusinessException e){var view=new org.springframework.web.servlet.ModelAndView("error");view.setStatus(Set.of("SCOPE","CONFLICT","BUILD_CHANGED").contains(e.code())?HttpStatus.CONFLICT:HttpStatus.BAD_REQUEST);view.addObject("message",e.getMessage());return view;}
    record Ticket(String region,String compartment,String pipeline,String revision) {}
    @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession session){var map=(Map<String,Ticket>)session.getAttribute("automationTickets");if(map==null){map=new LinkedHashMap<>();session.setAttribute("automationTickets",map);}return map;}
    @GetMapping("/builds/automation") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String pipeline){
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var s=catalog.selection(c,request.getSession());model.addAttribute("connection",c);model.addAttribute("selection",s);model.addAttribute("scopeBack","/builds/automation");model.addAttribute("tab","automation");model.addAttribute("pipeline",pipeline);
        model.addAttribute("pipelines",List.of());model.addAttribute("triggers",List.of());model.addAttribute("schedules",List.of());model.addAttribute("dispatchers",List.of());model.addAttribute("sources",List.of());
        if(request.getSession().getAttribute("scopeInvalid")!=null)return "build-automation";
        try{
            var pipelines=builds.pipelines(c,s.region(),s.compartment());model.addAttribute("pipelines",pipelines);if(pipeline.isEmpty())return "build-automation";
            if(pipelines.stream().noneMatch(p->p.id().equals(pipeline)))throw Errors.MISSING.exception();
            var definition=definitions.read(c,s.region(),s.compartment(),pipeline);model.addAttribute("definition",definition);model.addAttribute("sources",definition.sources());
            try{model.addAttribute("triggers",automation.triggers(c,s.region(),s.compartment(),pipeline));}catch(BusinessException e){model.addAttribute("triggerError",e.getMessage());}
            try{model.addAttribute("dispatchers",automation.dispatchers(c,s.region(),s.compartment(),pipeline));}catch(BusinessException e){model.addAttribute("dispatcherError",e.getMessage());}
            try{model.addAttribute("schedules",automation.schedules(c,s.region(),s.compartment(),pipeline));}catch(BusinessException e){model.addAttribute("scheduleError",e.getMessage());}
            String token=UUID.randomUUID().toString();synchronized(request.getSession()){var map=tickets(request.getSession());if(map.size()>=32)map.remove(map.keySet().iterator().next());map.put(token,new Ticket(s.region(),s.compartment(),pipeline,definition.revision()));}model.addAttribute("ticket",token);
        }catch(BusinessException e){model.addAttribute("message",e.getMessage());}return "build-automation";
    }
    private Ticket take(Authentication auth,HttpSession session,String token){return ticket(auth,session,token,true);}
    private Ticket ticket(Authentication auth,HttpSession session,String token,boolean consume){Ticket t;synchronized(session){t=consume?tickets(session).remove(token):tickets(session).get(token);}if(t==null)throw Errors.CONFLICT.exception();var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(session.getAttribute("scopeInvalid")!=null || !s.region().equals(t.region()) || !s.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();if(!definitions.read(c,t.region(),t.compartment(),t.pipeline()).revision().equals(t.revision()))throw Errors.BUILD_CHANGED.exception();return t;}
    @PostMapping("/builds/automation/trigger") String trigger(Authentication auth,HttpSession session,Model model,@RequestParam String ticket,@RequestParam String source,@RequestParam String name,@RequestParam String event,@RequestParam String branch,@RequestParam boolean confirmed){
        if(!confirmed)throw Errors.INVALID.exception();var t=take(auth,session,ticket);var c=SecurityConfig.connection(auth);var result=automation.createTrigger(c,t.region(),t.compartment(),t.pipeline(),source,name,event,branch,ticket);
        model.addAttribute("created",result);model.addAttribute("pipeline",t.pipeline());return "build-trigger-created";
    }
    @PostMapping("/builds/automation/schedule") String schedule(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam String function,@RequestParam String name,@RequestParam String cron,@RequestParam boolean confirmed){
        if(!confirmed)throw Errors.INVALID.exception();var t=take(auth,session,ticket);String id=automation.createSchedule(SecurityConfig.connection(auth),t.region(),t.compartment(),t.pipeline(),function,name,cron,ticket);flash.addFlashAttribute("message","예약 생성 · "+id+" · IAM·실행 상태를 확인하세요.");return "redirect:/builds/automation?pipeline="+t.pipeline();
    }
    @PostMapping("/builds/automation/state") String state(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam String id,@RequestParam String etag,@RequestParam boolean active){var t=take(auth,session,ticket);automation.scheduleState(SecurityConfig.connection(auth),t.region(),t.compartment(),t.pipeline(),id,etag,active);flash.addFlashAttribute("message","예약 상태 변경 요청 완료 · 상태를 다시 조회하세요.");return "redirect:/builds/automation?pipeline="+t.pipeline();}
    @GetMapping("/builds/automation/preview") @ResponseBody Object preview(@RequestParam String cron){return BuildSchedules.next(cron,java.time.Instant.now());}
    @PostMapping("/builds/automation/template") ResponseEntity<byte[]> template(Authentication auth,HttpSession session,@RequestParam String ticket){var t=ticket(auth,session,ticket,false);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=devops-build-dispatch.zip").body(FunctionTemplate.zip(BuildDispatchTemplate.files(t.pipeline(),t.region(),t.compartment())));}
}
