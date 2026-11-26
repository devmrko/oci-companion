package io.github.devmrko.companion.web;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.PipelineIam;
import io.github.devmrko.companion.service.Errors;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.Instant;
import java.util.*;

@Controller @Profile("!legacy")
public class PipelineIamPages {
    private final CatalogPages catalog; private final PipelineIam iam;
    public PipelineIamPages(CatalogPages catalog,PipelineIam iam){this.catalog=catalog;this.iam=iam;}
    private io.github.devmrko.companion.port.ActionPermissions permissions;
    @org.springframework.beans.factory.annotation.Autowired void permissions(io.github.devmrko.companion.port.ActionPermissions permissions){this.permissions=permissions;}
    private io.github.devmrko.companion.port.ActionPermissions.Evidence permission(Connection c,String compartment,PipelineIam.Check check){
        var policy=permissions.inspect(c,compartment,"policies");
        if(!check.createGroup())return policy;
        var group=permissions.inspect(c,c.tenancy(),"dynamic-groups");
        var state=policy.permission()==io.github.devmrko.companion.service.Readiness.Permission.DENIED||group.permission()==io.github.devmrko.companion.service.Readiness.Permission.DENIED?io.github.devmrko.companion.service.Readiness.Permission.DENIED:policy.permission()==io.github.devmrko.companion.service.Readiness.Permission.CONFIRMED&&group.permission()==io.github.devmrko.companion.service.Readiness.Permission.CONFIRMED?io.github.devmrko.companion.service.Readiness.Permission.CONFIRMED:io.github.devmrko.companion.service.Readiness.Permission.UNKNOWN;
        return new io.github.devmrko.companion.port.ActionPermissions.Evidence(state,policy.reason()+" / "+group.reason(),policy.guidance()+"\n"+group.guidance());
    }
    record Preview(Connection actor,String region,String compartment,String pipeline,PipelineIam.Check check,Instant expires) {}
    @SuppressWarnings("unchecked") Map<String,Preview> previews(HttpSession session) {
        var map=(Map<String,Preview>)session.getAttribute("pipelineIamPreviews");
        if(map==null){map=new LinkedHashMap<>();session.setAttribute("pipelineIamPreviews",map);}return map;
    }
    @GetMapping("/builds/iam") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam String pipeline) {
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var session=request.getSession();var s=catalog.selection(c,session);
        if(session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        var check=iam.inspect(c,s.region(),s.compartment(),pipeline);
        var permission=permission(c,s.compartment(),check);
        model.addAttribute("permission",permission);
        model.addAttribute("iamCheck",new io.github.devmrko.companion.service.Readiness.Check("iam","파이프라인 IAM",check.ready()?io.github.devmrko.companion.service.Readiness.State.PASS:check.inspected()?io.github.devmrko.companion.service.Readiness.State.FAIL:io.github.devmrko.companion.service.Readiness.State.UNKNOWN,check.inspected()||permission.permission()==io.github.devmrko.companion.service.Readiness.Permission.DENIED?permission.permission():io.github.devmrko.companion.service.Readiness.Permission.UNKNOWN,check.inspected()?permission.reason():check.error(),"부족한 권한만 새 정책으로 생성합니다.",permission.guidance(),"",""));
        model.addAttribute("check",check);model.addAttribute("pipeline",pipeline);model.addAttribute("connection",c);
        model.addAttribute("tab","pipelines");model.addAttribute("scopeBack","/builds");
        String token=UUID.randomUUID().toString();
        synchronized(session){var map=previews(session);if(map.size()>=16)map.remove(map.keySet().iterator().next());map.put(token,new Preview(c,s.region(),s.compartment(),pipeline,check,Instant.now().plusSeconds(600)));}
        model.addAttribute("ticket",token);
        String common=" --profile "+quote(c.profile());
        String cli="";
        if(check.inspected()&&!check.missing().isEmpty()) {
            if(check.createGroup())cli="oci iam dynamic-group create"+common+" --compartment-id "+quote(c.tenancy())+" --name "+quote(check.groupName())+" --description 'Companion pipeline principals' --matching-rule "+quote(check.rule())+"\n";
            try{cli+="oci iam policy create"+common+" --compartment-id "+quote(s.compartment())+" --name "+quote("companion-pipeline-"+token.substring(0,12))+" --description 'Companion pipeline permissions' --statements "+quote(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(check.missing()));}
            catch(Exception e){throw Errors.INVALID.exception();}
        }
        model.addAttribute("iamCli",io.github.devmrko.companion.service.PipelineIamRules.safe(cli));return "pipeline-iam";
    }
    static String quote(String s){return "'"+s.replace("'","'\"'\"'")+"'";}
    @PostMapping("/builds/iam/create") String create(Authentication auth,HttpSession session,RedirectAttributes flash,@RequestParam String ticket,@RequestParam(defaultValue="false") boolean confirmed) {
        Preview p;synchronized(session){p=previews(session).remove(ticket);}
        if(!confirmed||p==null||p.expires().isBefore(Instant.now()))throw Errors.CONFLICT.exception();
        var c=SecurityConfig.connection(auth);var s=catalog.selection(c,session);
        if(!p.actor().equals(c)||!p.region().equals(s.region())||!p.compartment().equals(s.compartment())||session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        if(permission(c,p.compartment(),p.check()).permission()!=io.github.devmrko.companion.service.Readiness.Permission.CONFIRMED)throw Errors.SCOPE.exception();
        try{iam.create(c,p.region(),p.compartment(),p.pipeline(),p.check(),ticket);flash.addFlashAttribute("message","생성 완료. 권한 전파에 수 분이 걸릴 수 있습니다. 기다린 후 다시 점검하세요.");}
        catch(IllegalStateException e){flash.addFlashAttribute("message",e.getMessage());}
        return "redirect:/builds/iam?pipeline="+java.net.URLEncoder.encode(p.pipeline(),java.nio.charset.StandardCharsets.UTF_8);
    }
}
