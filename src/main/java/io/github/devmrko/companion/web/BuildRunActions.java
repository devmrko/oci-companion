package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.BuildControl;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @Profile("!legacy")
public class BuildRunActions {
    private final CatalogPages catalog;private final BuildControl control;
    public BuildRunActions(CatalogPages catalog,BuildControl control){this.catalog=catalog;this.control=control;}
    @ExceptionHandler(BusinessException.class) Object invalid(io.github.devmrko.companion.error.BusinessException e,Authentication a,jakarta.servlet.http.HttpServletRequest r){return OperationErrors.response(e,catalog,a,r);}
    @PostMapping("/builds/cancel") String cancel(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String region,@RequestParam String compartment,@RequestParam String pipeline,@RequestParam String run,@RequestParam boolean confirmed){
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);if(!confirmed || session.getAttribute("scopeInvalid")!=null || !region.equals(s.region()) || !compartment.equals(s.compartment()))throw Errors.SCOPE.exception();
        control.cancel(c,region,compartment,pipeline,run);flash.addFlashAttribute("message","취소 요청 접수 · 최종 상태는 실행 이력에서 확인하세요.");return "redirect:/builds?tab=history&pipeline="+pipeline+"&run="+run;
    }
}
