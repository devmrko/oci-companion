package io.github.devmrko.companion.web;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.http.HttpStatus;
final class OperationErrors {
 private OperationErrors() {}
 record Destination(String prefix,String path,String label) {}
 private static final java.util.List<Destination> DESTINATIONS=java.util.List.of(
  new Destination("/backups/policies","/backups/policies","정기 정책"),
  new Destination("/backups","/backups","백업·복구"),
  new Destination("/environments/containers","/environments/containers","Container 상태"),
  new Destination("/environments/oke","/environments/oke","OKE 환경 준비"),
  new Destination("/environments","/environments","배포 환경"),
  new Destination("/deployments/oke/configure","/deployments/oke/configure","OKE 배포 설정"),
  new Destination("/deployments/configure","/deployments/configure","VM 배포 설정"),
  new Destination("/builds/automation","/builds/automation","자동 실행"),
  new Destination("/builds/project-resources","/builds/project-resources","저장소·로그 준비"),
  new Destination("/builds/tasks","/builds/tasks","빌드 작업"),
  new Destination("/builds/functions","/builds/functions","Functions 빌드"),
  new Destination("/builds","/builds?tab=history","빌드 이력"),
  new Destination("/deployments/runs","/deployments/runs","배포 이력"),
  new Destination("/deployments/rollback","/deployments/runs","배포 이력"),
  new Destination("/deployments","/deployments","배포 환경"),
  new Destination("/sources","/sources","소스 목록"));
 static Destination destination(String path){return DESTINATIONS.stream().filter(d->path.equals(d.prefix())||path.startsWith(d.prefix()+"/")).findFirst().orElse(new Destination("/","/","리소스 목록"));}
 static HttpStatus status(BusinessException e){return switch(e.category()){case VALIDATION->HttpStatus.BAD_REQUEST;case CONFLICT->HttpStatus.CONFLICT;case NOT_FOUND->HttpStatus.NOT_FOUND;case UNAVAILABLE->HttpStatus.SERVICE_UNAVAILABLE;};}
 static Object response(BusinessException e,CatalogPages catalog,Authentication auth,HttpServletRequest request){
  Object handler=request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
  if(handler instanceof org.springframework.web.method.HandlerMethod method && method.hasMethodAnnotation(org.springframework.web.bind.annotation.ResponseBody.class))return org.springframework.http.ResponseEntity.status(status(e)).body(java.util.Map.of("code",e.code(),"message",e.getMessage()));
  return view(e,catalog,auth,request);
 }
 static ModelAndView view(BusinessException error,CatalogPages catalog,Authentication auth,HttpServletRequest request){
  var model=new ExtendedModelMap();try{catalog.navigation(auth,request,model);model.addAttribute("navSelection",catalog.selection(SecurityConfig.connection(auth),request.getSession()));}catch(Exception ignored){}
  model.addAttribute("connection",auth!=null?auth.getPrincipal():null);
  model.addAttribute("operationError",error.getMessage());
  model.addAttribute("operationCode",error.code());
  model.addAttribute("readRequest",request.getMethod().equals("GET"));
  String path=request.getRequestURI();
  model.addAttribute("backupPolicyError",path.startsWith("/backups/policies"));model.addAttribute("backupManualError",path.startsWith("/backups")&&!path.startsWith("/backups/policies"));
  var destination=destination(path);String back=destination.path();
  model.addAttribute("operationBack",back);model.addAttribute("operationBackLabel",destination.label());
  model.addAttribute("scopeBack",back.split("\\?",2)[0]);
  return new ModelAndView("operation-error",model,status(error));
 }
}
