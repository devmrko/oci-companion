package io.github.devmrko.companion.web;

import io.github.devmrko.companion.port.BuildHistory;
import io.github.devmrko.companion.error.BusinessException;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @Profile("!legacy")
public class BuildInsightPages {
    private final CatalogPages catalog; private final BuildHistory history;
    public BuildInsightPages(CatalogPages catalog,BuildHistory history){this.catalog=catalog;this.history=history;}
    @GetMapping("/builds/insights") String page(Authentication auth,HttpServletRequest req,Model model,
        @RequestParam String pipeline,@RequestParam(defaultValue="") String run,@RequestParam(defaultValue="") String previous,
        @RequestParam(defaultValue="28") int days,@RequestParam(defaultValue="false") boolean reports,@RequestParam(defaultValue="") String artifact,@RequestParam(defaultValue="") String previousArtifact){
        catalog.navigation(auth,req,model);model.addAttribute("scopeBack","/builds?tab=history");model.addAttribute("pipeline",pipeline);
        int period=Set.of(7,28,90).contains(days)?days:28;model.addAttribute("days",period);model.addAttribute("reports",reports);model.addAttribute("artifact",artifact);model.addAttribute("previousArtifact",previousArtifact);
        try {
            if(req.getSession().getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
            var c=SecurityConfig.connection(auth);var s=catalog.selection(c,req.getSession());
            var all=history.runs(c,s.region(),s.compartment(),pipeline);
            var recent=BuildInsights.window(all,Instant.now(),period);model.addAttribute("runs",recent);model.addAttribute("summary",BuildInsights.summarize(recent));
            String selected=run.isBlank()?(recent.isEmpty()?"":recent.getFirst().id()):run;
            if(selected.isBlank())return "build-insights";
            var current=all.stream().filter(r->r.id().equals(selected)).findFirst().orElseThrow(()->Errors.MISSING.exception());
            var chronological=all.stream().sorted(Comparator.comparing(BuildHistory.Run::createdAt).reversed().thenComparing(BuildHistory.Run::id)).toList();
            String prior=previous;
            if(prior.isBlank()){int i=chronological.indexOf(current);if(i>=0&&i+1<chronological.size())prior=chronological.get(i+1).id();}
            String chosen=prior;
            var before=chosen.isBlank()?null:all.stream().filter(r->r.id().equals(chosen)).findFirst().orElseThrow(()->Errors.MISSING.exception());
            if(before!=null&&before.id().equals(current.id()))throw new IllegalArgumentException("서로 다른 실행을 선택하세요.");
            var detail=history.detail(c,s.region(),s.compartment(),pipeline,current.id());
            var previousDetail=before==null?null:history.detail(c,s.region(),s.compartment(),pipeline,before.id());
            model.addAttribute("choices",chronological);model.addAttribute("current",current);model.addAttribute("previous",before);
            model.addAttribute("detail",detail);model.addAttribute("before",previousDetail);model.addAttribute("stages",BuildInsights.stages(detail,previousDetail));
            model.addAttribute("currentDuration",BuildInsights.duration(current.startedAt(),current.finishedAt()));
            model.addAttribute("currentWait",BuildInsights.duration(current.createdAt(),current.startedAt()));
            model.addAttribute("previousDuration",before==null?"기록 없음":BuildInsights.duration(before.startedAt(),before.finishedAt()));
            model.addAttribute("previousWait",before==null?"기록 없음":BuildInsights.duration(before.createdAt(),before.startedAt()));
            model.addAttribute("currentContext",history.executionContext(c,s.region(),s.compartment(),pipeline,current.id()));
            model.addAttribute("previousContext",before==null?Map.of():history.executionContext(c,s.region(),s.compartment(),pipeline,before.id()));
            if(reports){
                loadReport(model,"currentReport",c,s.region(),s.compartment(),pipeline,detail,artifact);
                if(previousDetail!=null)loadReport(model,"previousReport",c,s.region(),s.compartment(),pipeline,previousDetail,previousArtifact);
            }
        } catch(BusinessException|IllegalArgumentException e){model.addAttribute("insightError",e.getMessage());}
        return "build-insights";
    }
    private void loadReport(Model model,String key,io.github.devmrko.companion.domain.Models.Connection c,String region,String compartment,String pipeline,BuildHistory.Detail detail,String selected){
        var files=detail.artifacts().stream().filter(a->a.kind().equals("Artifact Registry")&&a.location().endsWith(".tar.gz")).toList();
        if(!selected.isBlank())files=files.stream().filter(a->a.id().equals(selected)).toList();
        if(files.size()!=1){model.addAttribute(key+"Error","자동 비교 불가 · 보고서 산출물을 선택하세요.");return;}
        try{model.addAttribute(key,history.reports(c,region,compartment,pipeline,detail.id(),files.getFirst().id()));}
        catch(BusinessException e){model.addAttribute(key+"Error","보고서 조회 실패 · 실행 상세에서 권한·파일을 확인하세요.");}
    }
}
