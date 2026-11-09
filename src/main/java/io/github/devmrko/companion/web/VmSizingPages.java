package io.github.devmrko.companion.web;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.VmSizing;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @Profile("!legacy")
public class VmSizingPages {
    private final CatalogPages catalog;private final VmSizing sizing;
    public VmSizingPages(CatalogPages catalog,VmSizing sizing){this.catalog=catalog;this.sizing=sizing;}
    record Ticket(Connection connection,String region,String compartment,VmSizing.Snapshot before,VmSizing.Change change,Instant expires) {}
    @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("vmSizingTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("vmSizingTickets",m);}return m;}
    @GetMapping("/environments/sizing") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam String instance) {
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);model.addAttribute("connection",c);model.addAttribute("scopeBack","/environments");
        if(request.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        var scope=catalog.selection(c,request.getSession());model.addAttribute("navSelection",scope);model.addAttribute("sizing",sizing.read(c,scope.region(),scope.compartment(),instance));return "vm-sizing";
    }
    @PostMapping("/environments/sizing/preview") String preview(Authentication auth,HttpServletRequest request,Model model,@RequestParam String instance,@RequestParam(defaultValue="") String volume,@RequestParam(defaultValue="0") float cpu,@RequestParam(defaultValue="0") float memory,@RequestParam(defaultValue="0") long size) {
        page(auth,request,model,instance);var before=(VmSizing.Snapshot)model.getAttribute("sizing");var change=new VmSizing.Change(volume,cpu,memory,size);VmSizing.validate(before,change);
        var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,request.getSession());
        synchronized(request.getSession()) {var map=tickets(request.getSession());map.entrySet().removeIf(x->x.getValue().expires().isBefore(Instant.now()));if(map.size()>=16)map.remove(map.keySet().iterator().next());String token=UUID.randomUUID().toString();map.put(token,new Ticket(c,scope.region(),scope.compartment(),before,change,Instant.now().plusSeconds(600)));model.addAttribute("ticket",token);}
        model.addAttribute("change",change);if(!volume.isBlank())model.addAttribute("volumeBefore",before.volumes().stream().filter(x->x.id().equals(volume)).findFirst().orElseThrow());return "vm-sizing";
    }
    @PostMapping("/environments/sizing/apply") String apply(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket) {
        Ticket t;synchronized(session){t=tickets(session).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();
        var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);
        if(!c.equals(t.connection())||session.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();
        try{sizing.apply(c,t.region(),t.compartment(),t.before(),t.change());flash.addFlashAttribute("message","OCI 변경 요청 접수 · 상태와 사양을 새로 조회하세요.");}catch(BusinessException e){flash.addFlashAttribute("message",e.getMessage());}
        return "redirect:/environments/sizing?instance="+java.net.URLEncoder.encode(t.before().instance(),java.nio.charset.StandardCharsets.UTF_8);
    }
    @ExceptionHandler(BusinessException.class) @ResponseBody org.springframework.http.ResponseEntity<String> error(BusinessException e){return org.springframework.http.ResponseEntity.status(409).body(e.getMessage());}
}
