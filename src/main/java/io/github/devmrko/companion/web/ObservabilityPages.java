package io.github.devmrko.companion.web;

import io.github.devmrko.companion.port.ObservabilitySetup;
import io.github.devmrko.companion.adapter.AgentConnectionRegistry;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @Profile("!legacy")
public class ObservabilityPages {
 private final CatalogPages catalog;private final ObservabilitySetup setup;private final AgentConnectionRegistry registry;
 public ObservabilityPages(CatalogPages catalog,ObservabilitySetup setup,AgentConnectionRegistry registry){this.catalog=catalog;this.setup=setup;this.registry=registry;}
 private CatalogPages.Selection scope(Authentication a,HttpSession session){var c=SecurityConfig.connection(a);if(c.demo()||session.getAttribute("scopeInvalid")!=null)throw new IllegalArgumentException("실제 프로파일과 리전·구획을 선택하세요.");return catalog.selection(c,session);}
 @GetMapping("/agents/connections/observability/installation.zip") @ResponseBody org.springframework.http.ResponseEntity<byte[]> bundle(Authentication a,HttpSession session,@RequestParam String provider)throws java.io.IOException{
  scope(a,session);
  var files=setup.installationFiles(provider);
  var bytes=new java.io.ByteArrayOutputStream();
  try(var zip=new java.util.zip.ZipOutputStream(bytes)){
   for(var file:files.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(file.getKey()));zip.write(file.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
  }
  return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).header("Content-Disposition","attachment; filename="+provider+"-installation.zip").contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM).body(bytes.toByteArray());
 }
 @GetMapping("/agents/connections/observability/grafana-compose.yaml") @ResponseBody org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> installation(Authentication a,HttpSession session){
  scope(a,session);
  return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).header("Content-Disposition","attachment; filename=compose.yaml").contentType(org.springframework.http.MediaType.TEXT_PLAIN).body(new org.springframework.core.io.ClassPathResource("observability/grafana-compose.yaml"));
 }
 @GetMapping("/agents/connections/observability/environment.txt") @ResponseBody org.springframework.http.ResponseEntity<String> environment(Authentication a,HttpSession session,@RequestParam String selected){
  var s=scope(a,session);var settings=setup.get(SecurityConfig.connection(a),s.region(),s.compartment(),selected);
  if(settings==null)throw new IllegalArgumentException("연결 설정을 먼저 저장하세요.");
  return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).header("Content-Disposition","attachment; filename=observability-environment.txt").contentType(org.springframework.http.MediaType.TEXT_PLAIN).body(setup.instrumentation(settings));
 }
 @GetMapping("/agents/connections/observability") String page(Authentication a,HttpServletRequest request,Model m,@RequestParam String selected,@RequestParam(defaultValue="") String provider){
  catalog.navigation(a,request,m);var s=scope(a,request.getSession());var c=SecurityConfig.connection(a);var app=registry.get(c,s.region(),s.compartment(),selected);var saved=setup.get(c,s.region(),s.compartment(),selected);
  String product=provider.isBlank()?(saved==null?"grafana":saved.provider()):provider;var choice=setup.providers().stream().filter(p->p.id().equals(product)).findFirst().orElseThrow(()->new IllegalArgumentException("제품을 선택하세요."));
  m.addAttribute("selection",s);m.addAttribute("selected",app.id());m.addAttribute("app",app);m.addAttribute("providers",setup.providers());m.addAttribute("provider",choice);m.addAttribute("saved",saved);m.addAttribute("scopeBack","/agents/connections");
  if(saved!=null)m.addAttribute("instrumentation",setup.instrumentation(saved));
  return "observability-setup";
 }
 @PostMapping("/agents/connections/observability") String save(Authentication a,HttpSession session,RedirectAttributes flash,@RequestParam String selected,@RequestParam String region,@RequestParam String compartment,@RequestParam String provider,@RequestParam String dashboard,@RequestParam String endpoint,@RequestParam String service,@RequestParam String runtime,@RequestParam(defaultValue="") String agentId){
  var s=scope(a,session);if(!s.region().equals(region)||!s.compartment().equals(compartment))throw new IllegalArgumentException("리전·구획이 바뀌었습니다. 다시 확인하세요.");
  try{setup.save(SecurityConfig.connection(a),region,compartment,selected,new ObservabilitySetup.Settings(provider,dashboard.strip(),endpoint.strip(),service.strip(),runtime,agentId.strip()));flash.addFlashAttribute("message","연결 설정 저장 · 실제 수집은 분석 도구에서 확인하세요.");}catch(IllegalArgumentException e){flash.addFlashAttribute("message",e.getMessage());}
  return "redirect:/agents/connections/observability?selected="+java.net.URLEncoder.encode(selected,java.nio.charset.StandardCharsets.UTF_8);
 }
}
