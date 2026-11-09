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
  model.addAttribute("operationError",error.getMessage());model.addAttribute("scopeBack","/deployments/runs");return new ModelAndView("operation-error",model,HttpStatus.CONFLICT);
 }
}
