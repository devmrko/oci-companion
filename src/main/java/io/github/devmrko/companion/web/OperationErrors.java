package io.github.devmrko.companion.web;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.http.HttpStatus;
final class OperationErrors {
 private OperationErrors() {}
 static ModelAndView view(BusinessException error,CatalogPages catalog,Authentication auth,HttpServletRequest request){
  var model=new ExtendedModelMap();try{catalog.navigation(auth,request,model);model.addAttribute("navSelection",catalog.selection(SecurityConfig.connection(auth),request.getSession()));}catch(Exception ignored){}
  model.addAttribute("connection",auth!=null?auth.getPrincipal():null);
  model.addAttribute("operationError",error.getMessage());
  model.addAttribute("operationCode",error.code());
  model.addAttribute("readRequest",request.getMethod().equals("GET"));
  String path=request.getRequestURI();
  String back=path.startsWith("/builds")?"/builds?tab=history":path.startsWith("/deployments")?"/deployments/runs":path.startsWith("/sources")?"/sources":"/";
  model.addAttribute("operationBack",back);
  model.addAttribute("operationBackLabel",path.startsWith("/builds")?"빌드 이력":path.startsWith("/deployments")?"배포 이력":path.startsWith("/sources")?"소스 목록":"리소스 목록");
  model.addAttribute("scopeBack",back.equals("/builds?tab=history")?"/builds":back);
  var status=switch(error.category()){case VALIDATION -> HttpStatus.BAD_REQUEST;case CONFLICT -> HttpStatus.CONFLICT;case NOT_FOUND -> HttpStatus.NOT_FOUND;case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;};
  return new ModelAndView("operation-error",model,status);
 }
}
