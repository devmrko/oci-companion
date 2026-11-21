package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.RuntimeDiagnostics;
import io.github.devmrko.companion.service.Errors;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@Controller @Profile("!legacy")
public class RuntimeDiagnosticsPages {
 private final CatalogPages catalog;private final RuntimeDiagnostics diagnostics;
 public RuntimeDiagnosticsPages(CatalogPages catalog,RuntimeDiagnostics diagnostics){this.catalog=catalog;this.diagnostics=diagnostics;}
 record Stored(Connection c,String region,String compartment,String app,Instant expires,RuntimeDiagnostics.Dump dump) {}
 private CatalogPages.Selection scope(Authentication a,HttpSession session){var c=SecurityConfig.connection(a);if(c.demo()||session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();return catalog.selection(c,session);}
 private void base(Authentication a,HttpServletRequest r,Model m,String selected){catalog.navigation(a,r,m);var s=scope(a,r.getSession());m.addAttribute("connection",SecurityConfig.connection(a));m.addAttribute("navSelection",s);m.addAttribute("selection",s);m.addAttribute("selected",selected);m.addAttribute("scopeBack","/agents/connections");m.addAttribute("states",Map.of("PASS","수집 확인","CONFIGURED","설정 확인","PREPARE","준비 필요","UNKNOWN","미확인","UNSUPPORTED","미지원"));}
 @GetMapping("/agents/connections/diagnostics") String page(Authentication a,HttpServletRequest r,Model m,@RequestParam String selected,@RequestParam(defaultValue="false") boolean captured){base(a,r,m,selected);if(captured){download(a,r.getSession(),selected);m.addAttribute("dump",((Stored)r.getSession().getAttribute("runtimeDump")).dump());return "runtime-diagnostics";}var s=scope(a,r.getSession());try{m.addAttribute("report",diagnostics.inspect(SecurityConfig.connection(a),s.region(),s.compartment(),selected));}catch(IllegalArgumentException e){m.addAttribute("diagnosticError",e.getMessage());}return "runtime-diagnostics";}
 @PostMapping("/agents/connections/diagnostics/dump") String dump(Authentication a,HttpServletRequest r,Model m,@RequestParam String selected,@RequestParam String region,@RequestParam String compartment){var s=scope(a,r.getSession());if(!region.equals(s.region())||!compartment.equals(s.compartment()))throw Errors.SCOPE.exception();base(a,r,m,selected);r.getSession().removeAttribute("runtimeDump");try{var d=diagnostics.threadDump(SecurityConfig.connection(a),region,compartment,selected);r.getSession().setAttribute("runtimeDump",new Stored(SecurityConfig.connection(a),region,compartment,selected,Instant.now().plusSeconds(600),d));return "redirect:/agents/connections/diagnostics?selected="+java.net.URLEncoder.encode(selected,java.nio.charset.StandardCharsets.UTF_8)+"&captured=true";}catch(IllegalArgumentException e){m.addAttribute("diagnosticError",e.getMessage());}return "runtime-diagnostics";}
 @GetMapping("/agents/connections/diagnostics/dump.txt") @ResponseBody ResponseEntity<String> download(Authentication a,HttpSession session,@RequestParam String selected){var s=scope(a,session);var saved=(Stored)session.getAttribute("runtimeDump");if(saved==null||saved.expires().isBefore(Instant.now())||!saved.c().equals(SecurityConfig.connection(a))||!saved.region().equals(s.region())||!saved.compartment().equals(s.compartment())||!saved.app().equals(selected))throw Errors.SCOPE.exception();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=thread-dump.txt").contentType(MediaType.TEXT_PLAIN).body(saved.dump().text());}
 @PostMapping("/agents/connections/diagnostics/clear") String clear(Authentication a,HttpSession session,@RequestParam String selected){scope(a,session);session.removeAttribute("runtimeDump");return "redirect:/agents/connections/diagnostics?selected="+java.net.URLEncoder.encode(selected,java.nio.charset.StandardCharsets.UTF_8);}
}
