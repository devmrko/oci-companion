package io.github.devmrko.companion.web;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Source;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@Controller @Profile("!legacy")
public class NativeBuildSettingsPages {
    private final CatalogPages catalog;private final CatalogService sources;private final BuildHistory history;private final BuildDefinition definitions;private final BuildControl control;private final GitSpecifications git;private final BuildSpecDraftStore drafts;
    public NativeBuildSettingsPages(CatalogPages catalog,CatalogService sources,BuildHistory history,BuildDefinition definitions,BuildControl control,GitSpecifications git,BuildSpecDraftStore drafts){this.catalog=catalog;this.sources=sources;this.history=history;this.definitions=definitions;this.control=control;this.git=git;this.drafts=drafts;}
    record Binding(Connection connection,String region,String compartment,String pipeline,String definitionRevision,BuildDefinition.Source nativeSource,GitSpecifications.Target target) {}
    record Snapshot(Binding binding,String revision,String content,Instant expires) {}
    record Proposal(Snapshot snapshot,GitSpecifications.Target target,String content,String message) {}
    public record Edit(String token,BuildSpecDocument.Fields fields,String username,String secret,String message) {}
    @SuppressWarnings("unchecked") private <T> Map<String,T> items(HttpSession session,String name){var map=(Map<String,T>)session.getAttribute(name);if(map==null){map=new LinkedHashMap<>();session.setAttribute(name,map);}return map;}
    private <T> String keep(HttpSession session,String name,T value){synchronized(session){var map=this.<T>items(session,name);if(map.size()>=16)map.remove(map.keySet().iterator().next());String key=UUID.randomUUID().toString();map.put(key,value);return key;}}
    private CatalogPages.Selection scope(Authentication auth,HttpSession session,String region,String compartment){var s=catalog.selection(SecurityConfig.connection(auth),session);if(session.getAttribute("scopeInvalid")!=null||!s.region().equals(region)||!s.compartment().equals(compartment))throw Errors.SCOPE.exception();return s;}
    @GetMapping("/builds/settings") String page(Authentication auth,HttpServletRequest request,Model model,@RequestParam(defaultValue="") String pipeline,@RequestParam(defaultValue="") String stage){
        var c=SecurityConfig.connection(auth);catalog.navigation(auth,request,model);var s=catalog.selection(c,request.getSession());
        model.addAttribute("connection",c);model.addAttribute("selection",s);model.addAttribute("scopeBack","/builds/settings");model.addAttribute("tab","recipes");model.addAttribute("pipeline",pipeline);model.addAttribute("stage",stage);model.addAttribute("definition",null);model.addAttribute("nativeParameters",List.of());model.addAttribute("nativeStages",List.of());model.addAttribute("pipelineItems",List.of());model.addAttribute("specSources",List.of());
        if(request.getSession().getAttribute("scopeInvalid")!=null)return "build-settings";
        try { model.addAttribute("pipelineItems",history.pipelines(c,s.region(),s.compartment()));model.addAttribute("specSources",sources.sources(c,s.region(),s.compartment()));
        if(!pipeline.isBlank()){var definition=definitions.read(c,s.region(),s.compartment(),pipeline);model.addAttribute("definition",definition);model.addAttribute("nativeParameters",control.settings(c,s.region(),s.compartment(),pipeline).parameters());model.addAttribute("nativeStages",definition.stages().stream().filter(v->!v.sources().isEmpty()).toList());}
        } catch(io.github.devmrko.foundation.core.error.BusinessException error){model.addAttribute("nativePageError",error.getMessage());}
        return "build-settings";
    }
    @PostMapping("/builds/settings/load") @ResponseBody Map<String,Object> load(Authentication auth,HttpSession session,
        @RequestParam String region,@RequestParam String compartment,@RequestParam(defaultValue="") String pipeline,@RequestParam(defaultValue="") String stage,
        @RequestParam(defaultValue="") String source,@RequestParam(defaultValue="") String branch,@RequestParam(defaultValue="build_spec.yaml") String path,@RequestParam(defaultValue="") String username,@RequestParam(defaultValue="") String secret){
        scope(auth,session,region,compartment);var c=SecurityConfig.connection(auth);Binding binding;
        if(!pipeline.isBlank()){
            var definition=definitions.read(c,region,compartment,pipeline);var selected=definition.stages().stream().filter(v->v.id().equals(stage)).findFirst().orElseThrow(Errors.MISSING::exception);
            var primary=selected.sources().stream().filter(BuildDefinition.Source::primary).findFirst().orElseThrow(Errors.MISSING::exception);
            var nativeSource=new Source(primary.key(),primary.provider(),primary.name(),primary.url(),primary.branch(),primary.repositoryId());
            binding=new Binding(c,region,compartment,pipeline,definition.revision(),primary,new GitSpecifications.Target(nativeSource,primary.branch(),selected.spec(),username,secret));
        }else{
            var selected=sources.sources(c,region,compartment).stream().filter(v->v.id().equals(source)).findFirst().orElseThrow(Errors.MISSING::exception);
            binding=new Binding(c,region,compartment,"","",null,new GitSpecifications.Target(selected,branch,path,username,secret));
        }
        var file=read(binding,binding.target());if(file.content().isBlank()||file.revision().isBlank())throw Errors.MISSING.exception();var fields=BuildSpecDocument.read(file.content());
        String token=keep(session,"nativeSpecSnapshots",new Snapshot(binding,file.revision(),file.content(),Instant.now().plusSeconds(1800)));
        return Map.of("token",token,"revision",file.revision(),"content",file.content(),"fields",fields,"url",binding.target().source().url(),"branch",binding.target().branch(),"path",binding.target().path());
    }
    private GitSpecifications.File read(Binding binding,GitSpecifications.Target target){
        if(binding.nativeSource()!=null&&!binding.nativeSource().repositoryId().isBlank()){
            var spec=definitions.specification(binding.connection(),binding.region(),binding.nativeSource(),target.path());
            if(spec.content().isBlank()||spec.revision().isBlank())throw Errors.CLOUD.exception();return new GitSpecifications.File(spec.revision(),spec.content());
        }
        return git.read(binding.connection(),binding.region(),target);
    }
    private void check(Authentication auth,HttpSession session,Snapshot snapshot){
        if(snapshot==null||snapshot.expires().isBefore(Instant.now()))throw Errors.GIT_CONFLICT.exception();var b=snapshot.binding();
        scope(auth,session,b.region(),b.compartment());if(!b.connection().equals(SecurityConfig.connection(auth)))throw Errors.SCOPE.exception();
        if(!b.pipeline().isBlank()){
            if(!b.definitionRevision().equals(definitions.read(b.connection(),b.region(),b.compartment(),b.pipeline()).revision()))throw Errors.BUILD_CHANGED.exception();
        }else{
            var source=sources.sources(b.connection(),b.region(),b.compartment()).stream().filter(v->v.id().equals(b.target().source().id())).findFirst().orElseThrow(Errors.MISSING::exception);
            if(!source.equals(b.target().source()))throw Errors.GIT_CONFLICT.exception();
        }
    }
    private Snapshot localSnapshot(Authentication auth,HttpSession session,String token){
        Snapshot snapshot;synchronized(session){snapshot=this.<Snapshot>items(session,"nativeSpecSnapshots").get(token);}
        if(snapshot==null||snapshot.expires().isBefore(Instant.now()))throw Errors.GIT_CONFLICT.exception();
        var b=snapshot.binding();scope(auth,session,b.region(),b.compartment());if(!b.connection().equals(SecurityConfig.connection(auth)))throw Errors.SCOPE.exception();return snapshot;
    }
    private String draftKey(Snapshot snapshot){var b=snapshot.binding();var c=b.connection();var t=b.target();return String.join("\n",Boolean.toString(c.demo()),c.profile(),c.tenancy(),c.user(),b.region(),b.compartment(),b.pipeline(),t.source().id(),t.source().url(),t.branch(),t.path());}
    @PostMapping("/builds/settings/local/save") @ResponseBody Map<String,String> saveLocal(Authentication auth,HttpSession session,@RequestBody Edit edit){
        var snapshot=localSnapshot(auth,session,edit.token());String content=BuildSpecDocument.update(snapshot.content(),edit.fields());String now=Instant.now().toString();
        drafts.save(draftKey(snapshot),new BuildSpecDraftStore.Draft(snapshot.revision(),content,now));return Map.of("savedAt",now);
    }
    @PostMapping("/builds/settings/local/load") @ResponseBody Map<String,Object> loadLocal(Authentication auth,HttpSession session,@RequestParam String token){
        var snapshot=localSnapshot(auth,session,token);var draft=drafts.read(draftKey(snapshot)).orElseThrow(Errors.DRAFT_MISSING::exception);
        if(!draft.revision().equals(snapshot.revision()))throw Errors.DRAFT_STALE.exception();
        return Map.of("fields",BuildSpecDocument.read(draft.content()),"savedAt",draft.savedAt());
    }
    @PostMapping("/builds/settings/local/delete") @ResponseBody Map<String,String> deleteLocal(Authentication auth,HttpSession session,@RequestParam String token){var snapshot=localSnapshot(auth,session,token);drafts.delete(draftKey(snapshot));return Map.of("state","DELETED");}
    @PostMapping("/builds/settings/export") @ResponseBody Map<String,String> export(Authentication auth,HttpSession session,@RequestBody Edit edit){var snapshot=localSnapshot(auth,session,edit.token());return Map.of("content",BuildSpecDocument.update(snapshot.content(),edit.fields()));}
    @PostMapping("/builds/settings/preview") @ResponseBody Map<String,String> preview(Authentication auth,HttpSession session,@RequestBody Edit edit){
        Snapshot snapshot;synchronized(session){snapshot=this.<Snapshot>items(session,"nativeSpecSnapshots").get(edit.token());}check(auth,session,snapshot);
        if(edit.message()==null||edit.message().isBlank()||edit.message().length()>200||edit.message().chars().anyMatch(Character::isISOControl))throw Errors.INVALID.exception();
        var b=snapshot.binding();var target=new GitSpecifications.Target(b.target().source(),b.target().branch(),b.target().path(),Objects.toString(edit.username(),""),Objects.toString(edit.secret(),""));
        var current=read(b,target);if(!current.revision().equals(snapshot.revision())||!current.content().equals(snapshot.content()))throw Errors.GIT_CONFLICT.exception();
        String content=BuildSpecDocument.update(snapshot.content(),edit.fields());String ticket=keep(session,"nativeSpecProposals",new Proposal(snapshot,target,content,edit.message()));
        return Map.of("ticket",ticket,"before",snapshot.content(),"after",content,"revision",snapshot.revision());
    }
    @PostMapping("/builds/settings/save") @ResponseBody Map<String,String> save(Authentication auth,HttpSession session,@RequestParam String ticket){
        Proposal proposal;synchronized(session){proposal=this.<Proposal>items(session,"nativeSpecProposals").remove(ticket);}if(proposal==null)throw Errors.GIT_CONFLICT.exception();
        check(auth,session,proposal.snapshot());var b=proposal.snapshot().binding();
        String revision=git.save(b.connection(),b.region(),proposal.target(),proposal.snapshot().revision(),proposal.content(),proposal.message());
        return Map.of("revision",revision,"state",revision.equals(proposal.snapshot().revision())?"UNCHANGED":"SAVED");
    }
    @ExceptionHandler(io.github.devmrko.foundation.core.error.BusinessException.class)
    @ResponseBody ResponseEntity<Map<String,String>> error(io.github.devmrko.foundation.core.error.BusinessException error){return ResponseEntity.status(Set.of("CLOUD","GIT_ACCESS").contains(error.code())?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.CONFLICT).body(Map.of("message",error.getMessage()));}
}
