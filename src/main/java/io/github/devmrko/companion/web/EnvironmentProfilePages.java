package io.github.devmrko.companion.web;

import io.github.devmrko.companion.adapter.EnvironmentProfiles;
import io.github.devmrko.companion.adapter.EnvironmentProfiles.Document;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @Profile("!legacy")
public class EnvironmentProfilePages {
    private final EnvironmentProfiles profiles;
    public EnvironmentProfilePages(EnvironmentProfiles profiles){this.profiles=profiles;}
    private String render(Connection c,Model model,String selected,long revision,Document document,int page){
        var all=profiles.list(c);int last=Math.max(0,(all.size()-1)/20);int current=Math.max(0,Math.min(page,last));
        model.addAttribute("connection",c);model.addAttribute("items",all.subList(current*20,Math.min(all.size(),current*20+20)));
        model.addAttribute("page",current);model.addAttribute("last",last);model.addAttribute("total",all.size());
        model.addAttribute("templates",profiles.templates());model.addAttribute("chosenTemplate",profiles.template(document.template()));
        model.addAttribute("selected",selected);model.addAttribute("revision",revision);model.addAttribute("document",document);
        model.addAttribute("assessment",profiles.assess(document));
        return "environment-profiles";
    }
    @GetMapping("/settings/profiles") String page(Authentication auth,Model model,
        @RequestParam(defaultValue="") String selected,@RequestParam(defaultValue="demo-token") String template,@RequestParam(defaultValue="0") int page){
        var c=SecurityConfig.connection(auth);
        if(selected.isBlank())return render(c,model,"",0,new Document(1,1,"새 프로파일",template,Map.of()),page);
        var stored=profiles.get(c,selected);return render(c,model,stored.id(),stored.revision(),stored.document(),page);
    }
    @PostMapping("/settings/profiles") String save(Authentication auth,Model model,RedirectAttributes flash,
        @RequestParam(defaultValue="") String selected,@RequestParam(defaultValue="0") long revision,
        @RequestParam String name,@RequestParam String template,@RequestParam Map<String,String> form){
        var c=SecurityConfig.connection(auth);var values=new LinkedHashMap<String,String>();
        form.forEach((k,v)->{if(k.startsWith("v."))values.put(k.substring(2),v.strip());});
        var d=new Document(1,1,name.strip(),template,values);var assessment=profiles.assess(d);
        if(!assessment.errors().isEmpty()){model.addAttribute("message","저장하지 않았습니다. 잘못된 값을 수정하세요.");return render(c,model,selected,revision,d,0);}
        var stored=profiles.save(c,selected,revision,d);flash.addFlashAttribute("message","프로파일을 로컬에 저장했습니다. 실제 환경에는 적용하지 않았습니다.");
        return "redirect:/settings/profiles?selected="+stored.id();
    }
    @PostMapping("/settings/profiles/import") String preview(Authentication auth,Model model,@RequestParam("file") MultipartFile file)throws java.io.IOException{
        var c=SecurityConfig.connection(auth);
        if(file.getSize()>262144)throw new IllegalArgumentException("JSON 파일은 262144바이트 이하여야 합니다.");
        Document d;try(var stream=file.getInputStream()){d=profiles.previewImport(stream.readNBytes(262145));}
        model.addAttribute("message","가져오기 미리보기입니다. 대상 환경 값을 확인하고 저장하면 새 프로파일이 생성됩니다. 자격증명 연결은 별도로 필요합니다.");
        return render(c,model,"",0,d,0);
    }
    @GetMapping("/settings/profiles/{id}/export") ResponseEntity<String> export(Authentication auth,@PathVariable String id){
        var body=profiles.export(SecurityConfig.connection(auth),id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=environment-profile-"+id+".json").body(body);
    }
    @ExceptionHandler(IllegalArgumentException.class) ModelAndView invalid(IllegalArgumentException e){
        var view=new ModelAndView("error");view.setStatus(HttpStatus.BAD_REQUEST);view.addObject("message",e.getMessage());return view;
    }
}
