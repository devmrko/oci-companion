package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Errors;
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
public class NodePortAccessPages {
 private final CatalogPages catalog;private final OkeWorkloadConnections connections;private final NodePortAccess access;
 public NodePortAccessPages(CatalogPages catalog,OkeWorkloadConnections connections,NodePortAccess access){this.catalog=catalog;this.connections=connections;this.access=access;}
 private ActionPermissions permissions;
 @org.springframework.beans.factory.annotation.Autowired void permissions(ActionPermissions permissions){this.permissions=permissions;}
 record Ticket(Connection actor,String region,String compartment,NodePortAccess.Preview preview,Instant expires){}
 @SuppressWarnings("unchecked") Map<String,Ticket> tickets(HttpSession s){var map=(Map<String,Ticket>)s.getAttribute("nodePortAccessTickets");if(map==null){map=new LinkedHashMap<>();s.setAttribute("nodePortAccessTickets",map);}return map;}
 @GetMapping("/environments/oke/nodeport") String page(Authentication a,HttpServletRequest r,Model m,@RequestParam(defaultValue="") String cluster,@RequestParam(defaultValue="") String list,@RequestParam(defaultValue="") String source,@RequestParam(defaultValue="30080") int port,@RequestParam(defaultValue="false") boolean remove){
  var c=SecurityConfig.connection(a);catalog.navigation(a,r,m);var scope=catalog.selection(c,r.getSession());if(c.demo()||r.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
  m.addAttribute("selectedList",list);m.addAttribute("source",source);m.addAttribute("port",port);m.addAttribute("remove",remove);
  m.addAttribute("scopeBack","/environments/oke/nodeport");m.addAttribute("cluster",cluster);m.addAttribute("clusters",connections.clusters(c,scope.region(),scope.compartment()));
  if(!cluster.isBlank())try{
   m.addAttribute("lists",access.lists(c,scope.region(),scope.compartment(),cluster));
   if(!list.isBlank()){
    var preview=access.preview(c,scope.region(),scope.compartment(),cluster,list,source,port,remove);m.addAttribute("preview",preview);var permission=permissions.securityList(c,scope.region(),list);m.addAttribute("permission",permission);m.addAttribute("accessCheck",new io.github.devmrko.companion.service.Readiness.Check("security","워커 보안 규칙",io.github.devmrko.companion.service.Readiness.State.FAIL,permission.permission(),permission.reason(),"표시된 규칙을 백업 후 변경합니다.",permission.guidance(),"",""));String key=UUID.randomUUID().toString();
    synchronized(r.getSession()){var map=tickets(r.getSession());if(map.size()>=16)map.remove(map.keySet().iterator().next());map.put(key,new Ticket(c,scope.region(),scope.compartment(),preview,Instant.now().plusSeconds(600)));}m.addAttribute("ticket",key);
   }
  }catch(IllegalStateException e){String error=io.github.devmrko.companion.service.PipelineIamRules.safe(e.getMessage());m.addAttribute("message",error);m.addAttribute("checkFailure",new io.github.devmrko.companion.service.Readiness.Check("security","워커 보안 규칙",io.github.devmrko.companion.service.Readiness.State.UNKNOWN,error.contains("NotAuthorized (403)")?io.github.devmrko.companion.service.Readiness.Permission.DENIED:io.github.devmrko.companion.service.Readiness.Permission.UNKNOWN,error,"","워커 서브넷의 보안 목록을 조회하고 규칙을 관리할 권한이 필요합니다.\nAllow group <관리자 그룹> to manage security-lists in compartment id <보안 목록 구획 OCID>","",""));}return "nodeport-access";
 }
 @PostMapping("/environments/oke/nodeport") String execute(Authentication a,HttpSession s,RedirectAttributes flash,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed){
  Ticket t;synchronized(s){t=tickets(s).remove(ticket);}var c=SecurityConfig.connection(a);var scope=catalog.selection(c,s);
  if(!confirmed||t==null||t.expires().isBefore(Instant.now())||!t.actor().equals(c)||!t.region().equals(scope.region())||!t.compartment().equals(scope.compartment())||s.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
  if(permissions.securityList(c,t.region(),t.preview().list()).permission()!=io.github.devmrko.companion.service.Readiness.Permission.CONFIRMED)throw Errors.SCOPE.exception();
  try{access.execute(c,t.region(),t.compartment(),t.preview(),ticket);flash.addFlashAttribute("message","변경 전 백업 후 규칙 변경을 완료했습니다.");}catch(IllegalStateException e){flash.addFlashAttribute("message",e.getMessage());}
  return "redirect:/environments/oke/nodeport?cluster="+java.net.URLEncoder.encode(t.preview().cluster(),java.nio.charset.StandardCharsets.UTF_8);
 }
}
