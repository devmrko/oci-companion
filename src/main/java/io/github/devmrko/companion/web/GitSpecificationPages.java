package io.github.devmrko.companion.web;
import io.github.devmrko.companion.port.GitSpecifications;
import io.github.devmrko.companion.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.Instant;
import java.util.*;

@RestController @Profile("!legacy")
public class GitSpecificationPages {
    private final GitSpecifications git;private final CatalogPages catalog;private final CatalogService sources;
    public GitSpecificationPages(GitSpecifications git,CatalogPages catalog,CatalogService sources){this.git=git;this.catalog=catalog;this.sources=sources;}
    record Draft(String profile,String region,String compartment,GitSpecifications.Target target,String revision,String content,String message,Instant expires) {}
    @SuppressWarnings("unchecked") private Map<String,Draft> drafts(HttpSession session){var drafts=(Map<String,Draft>)session.getAttribute("gitSpecDrafts");if(drafts==null){drafts=new LinkedHashMap<>();session.setAttribute("gitSpecDrafts",drafts);}return drafts;}
    @ExceptionHandler(io.github.devmrko.foundation.core.error.BusinessException.class)
    ResponseEntity<Map<String,String>> error(io.github.devmrko.foundation.core.error.BusinessException error){return ResponseEntity.status(error.code().equals("GIT_ACCESS")?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.CONFLICT).body(Map.of("message",error.getMessage()));}
    @PostMapping("/builds/specification/git/preview") Map<String,String> preview(Authentication auth,HttpSession session,
        @RequestParam String source,@RequestParam String branch,@RequestParam String path,@RequestParam String username,@RequestParam String secret,@RequestParam String content,@RequestParam String message) {
        if(content.isBlank()||content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>131072||message.isBlank()||message.length()>200||message.chars().anyMatch(Character::isISOControl))throw Errors.INVALID.exception();
        var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);if(session.getAttribute("scopeInvalid")!=null)throw Errors.SCOPE.exception();
        var selected=sources.sources(c,scope.region(),scope.compartment()).stream().filter(s->s.id().equals(source)).findFirst().orElseThrow(Errors.MISSING::exception);
        var target=new GitSpecifications.Target(selected,branch,path,username,secret);var old=git.read(c,scope.region(),target);String ticket=UUID.randomUUID().toString();
        synchronized(session){var map=drafts(session);map.values().removeIf(d->d.expires().isBefore(Instant.now()));if(map.size()>=8)map.remove(map.keySet().iterator().next());map.put(ticket,new Draft(c.profile(),scope.region(),scope.compartment(),target,old.revision(),content,message,Instant.now().plusSeconds(900)));}
        return Map.of("ticket",ticket,"revision",old.revision(),"before",old.content(),"after",content,"target",selected.name()+" · "+branch+" · "+path);
    }
    @PostMapping("/builds/specification/git/save") Map<String,String> save(Authentication auth,HttpSession session,@RequestParam String ticket) {
        Draft draft;synchronized(session){draft=drafts(session).remove(ticket);}if(draft==null||draft.expires().isBefore(Instant.now()))throw Errors.GIT_CONFLICT.exception();
        var c=SecurityConfig.connection(auth);var scope=catalog.selection(c,session);
        if(session.getAttribute("scopeInvalid")!=null||!draft.profile().equals(c.profile())||!draft.region().equals(scope.region())||!draft.compartment().equals(scope.compartment()))throw Errors.SCOPE.exception();
        var current=sources.sources(c,scope.region(),scope.compartment()).stream().filter(s->s.id().equals(draft.target().source().id())).findFirst().orElseThrow(Errors.MISSING::exception);
        if(!current.equals(draft.target().source()))throw Errors.GIT_CONFLICT.exception();
        String revision=git.save(c,scope.region(),draft.target(),draft.revision(),draft.content(),draft.message());
        return Map.of("revision",revision,"state",revision.equals(draft.revision())?"UNCHANGED":"SAVED");
    }
}
