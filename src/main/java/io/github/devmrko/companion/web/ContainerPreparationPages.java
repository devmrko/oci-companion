package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
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
public class ContainerPreparationPages {
 private final CatalogPages catalog;private final ContainerPreparation preparation;private final Registry registry;
 public ContainerPreparationPages(CatalogPages catalog,ContainerPreparation preparation,Registry registry){this.catalog=catalog;this.preparation=preparation;this.registry=registry;}
 record Ticket(Connection c,String region,String compartment,ContainerPreparation.Preview preview,Instant expires) {}
 @SuppressWarnings("unchecked") private Map<String,Ticket> tickets(HttpSession s){var m=(Map<String,Ticket>)s.getAttribute("containerPreparationTickets");if(m==null){m=new LinkedHashMap<>();s.setAttribute("containerPreparationTickets",m);}return m;}
 @GetMapping("/environments/containers") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String domain,@RequestParam(defaultValue="") String shape,@RequestParam(defaultValue="") String repository,@RequestParam(defaultValue="") String imagePage,@RequestParam(defaultValue="") String instance,@RequestParam(defaultValue="") String container){
  var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);model.addAttribute("connection",c);model.addAttribute("scopeBack","/environments/containers");model.addAttribute("domain",domain);model.addAttribute("shape",shape);model.addAttribute("repository",repository);
  if(request.getSession().getAttribute("scopeInvalid")!=null)return "container-preparation";var scope=catalog.selection(c,request.getSession());model.addAttribute("navSelection",scope);
  if(!instance.isBlank())try{model.addAttribute("detail",preparation.read(c,scope.region(),scope.compartment(),instance));if(!container.isBlank())model.addAttribute("logs",preparation.logs(c,scope.region(),scope.compartment(),instance,container));}catch(BusinessException e){model.addAttribute("detailError",e.getMessage());}
  try{model.addAttribute("instances",preparation.list(c,scope.region(),scope.compartment()));}catch(BusinessException e){model.addAttribute("listError",e.getMessage());}
  if(!instance.isBlank())return "container-preparation";
  model.addAttribute("images",new Registry.Page(List.of(),""));
  try{model.addAttribute("pullSecrets",preparation.pullSecrets(c,scope.region(),scope.compartment()));}catch(BusinessException e){model.addAttribute("pullSecretError","Vault 목록 조회 실패 · secrets 조회 권한을 확인하세요. 공개 이미지·OCIR 리소스 주체 IAM에는 Vault Secret이 필요하지 않습니다.");}
  try{var options=preparation.options(c,scope.region(),scope.compartment(),domain);model.addAttribute("options",options);model.addAttribute("selectedShape",options.shapes().stream().filter(x->x.name().equals(shape)).findFirst().orElse(null));model.addAttribute("repositories",registry.list(c,scope.region(),scope.compartment(),"CONTAINER"));if(!repository.isBlank())model.addAttribute("images",registry.artifacts(c,scope.region(),scope.compartment(),"CONTAINER",repository,imagePage));}catch(BusinessException e){model.addAttribute("message",e.getMessage());}
  return "container-preparation";
 }
 static List<String> lines(String text){return text.lines().filter(x->!x.isBlank()).toList();}
 static Map<String,String> variables(String text){var map=new LinkedHashMap<String,String>();for(String line:lines(text)){int p=line.indexOf('=');if(p<1||map.putIfAbsent(line.substring(0,p).strip(),line.substring(p+1))!=null)throw Errors.INVALID.exception();}return Map.copyOf(map);}
 @PostMapping("/environments/containers/preview") String preview(Authentication auth,HttpServletRequest request,Model model,@RequestParam String name,@RequestParam String domain,@RequestParam String shape,@RequestParam String repository,@RequestParam float cpu,@RequestParam float memory,@RequestParam String subnet,@RequestParam(defaultValue="false") boolean publicIp,@RequestParam(defaultValue="") String image,@RequestParam(defaultValue="") String externalImage,@RequestParam(defaultValue="") String pullSecret,@RequestParam(defaultValue="") String variables,@RequestParam(defaultValue="") String command,@RequestParam(defaultValue="") String arguments,@RequestParam(defaultValue="0") int healthPort,@RequestParam(defaultValue="") String healthPath){
  var c=SecurityConfig.connection(auth);if(request.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();var scope=catalog.selection(c,request.getSession());
  var input=new ContainerPreparation.Input(name,domain,shape,cpu,memory,subnet,publicIp,image,variables(variables),lines(command),lines(arguments),healthPort,healthPath,externalImage.strip(),pullSecret.strip());var preview=preparation.preview(c,scope.region(),scope.compartment(),input);
  page(auth,request,model,domain,shape,repository,"","","");model.addAttribute("preview",preview);
  synchronized(request.getSession()){var m=tickets(request.getSession());m.entrySet().removeIf(x->x.getValue().expires().isBefore(Instant.now()));if(m.size()>=16)m.remove(m.keySet().iterator().next());String token=UUID.randomUUID().toString();m.put(token,new Ticket(c,scope.region(),scope.compartment(),preview,Instant.now().plusSeconds(600)));model.addAttribute("ticket",token);}return "container-preparation";
 }
 private Ticket checked(Authentication auth,HttpSession session,String token,boolean consume){Ticket t;synchronized(session){t=consume?tickets(session).remove(token):tickets(session).get(token);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(!c.equals(t.c())||session.getAttribute("scopeInvalid")!=null||!s.region().equals(t.region())||!s.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();return t;}
 @GetMapping("/environments/containers/request.json") @ResponseBody org.springframework.http.ResponseEntity<String> download(Authentication auth,HttpSession session,@RequestParam String ticket){var t=checked(auth,session,ticket,false);return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=container-instance.json").contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(t.preview().json());}
 @PostMapping("/environments/containers/create") String create(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket){var t=checked(auth,session,ticket,true);try{var id=preparation.create(t.c(),t.region(),t.compartment(),t.preview(),ticket);flash.addFlashAttribute("message","생성 요청 접수 · "+id+" · 새로 조회하여 OCI 상태를 확인하세요.");return "redirect:/environments/containers?instance="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);}catch(BusinessException e){flash.addFlashAttribute("message",e.getMessage());}return "redirect:/environments/containers";}
 record ActionTicket(Connection c,String region,String compartment,ContainerPreparation.Operation operation,Instant expires) {}
 @SuppressWarnings("unchecked") private Map<String,ActionTicket> actionTickets(HttpSession session){
  var map=(Map<String,ActionTicket>)session.getAttribute("containerActionTickets");if(map==null){map=new LinkedHashMap<>();session.setAttribute("containerActionTickets",map);}return map;
 }
 @PostMapping("/environments/containers/action/preview") String actionPreview(Authentication auth,HttpServletRequest request,Model model,@RequestParam String instance,@RequestParam ContainerPreparation.Action action){
  var c=SecurityConfig.connection(auth);var session=request.getSession();if(session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();var scope=catalog.selection(c,session);
  var operation=preparation.planAction(c,scope.region(),scope.compartment(),instance,action);
  page(auth,request,model,"","","","",instance,"");model.addAttribute("operation",operation);
  synchronized(session){var map=actionTickets(session);map.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));if(map.size()>=16)map.remove(map.keySet().iterator().next());var token=UUID.randomUUID().toString();map.put(token,new ActionTicket(c,scope.region(),scope.compartment(),operation,Instant.now().plusSeconds(600)));model.addAttribute("actionTicket",token);}
  return "container-preparation";
 }
 @PostMapping("/environments/containers/action/apply") String actionApply(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam(defaultValue="") String confirmation){
  ActionTicket t;synchronized(session){t=actionTickets(session).remove(ticket);}if(t==null||t.expires().isBefore(Instant.now()))throw Errors.BUILD_CHANGED.exception();
  var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);if(!c.equals(t.c())||session.getAttribute("scopeInvalid")!=null||!scope.region().equals(t.region())||!scope.compartment().equals(t.compartment()))throw Errors.SCOPE.exception();
  if(t.operation().action()==ContainerPreparation.Action.DELETE&&!t.operation().name().equals(confirmation))throw Errors.INVALID.exception();
  preparation.applyAction(c,scope.region(),scope.compartment(),t.operation());flash.addFlashAttribute("message",t.operation().action().label()+" 요청 접수 · 상태 새로 조회로 완료 여부를 확인하세요.");return "redirect:/environments/containers?instance="+java.net.URLEncoder.encode(t.operation().instance(),java.nio.charset.StandardCharsets.UTF_8);
 }
 @ExceptionHandler(BusinessException.class) org.springframework.web.servlet.ModelAndView error(BusinessException e,Authentication a,HttpServletRequest r){return OperationErrors.view(e,catalog,a,r);}
}
