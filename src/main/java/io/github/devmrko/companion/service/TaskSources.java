package io.github.devmrko.companion.service;
import io.github.devmrko.companion.domain.Catalog.Source;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildDefinition;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class TaskSources {
    private final CatalogService catalog;
    public TaskSources(CatalogService catalog){this.catalog=catalog;}
    public Source resolve(Connection c,String region,String compartment,String id,String url,String branch) {
        if(!id.isEmpty()) {
            var source=catalog.sources(c,region,compartment).stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(Errors.MISSING::exception);
            if(!url.isEmpty() && !sameUrl(url,source.url()))throw Errors.SOURCE_MISMATCH.exception();return source;
        }
        try{var u=URI.create(url);if(!"https".equals(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null || u.getPort()!=-1 || url.length()>2048)throw Errors.INVALID.exception();}
        catch(IllegalArgumentException e){throw Errors.INVALID.exception();}
        return new Source("","EXTERNAL","직접 입력",url,branch,"");
    }
    public static boolean sameUrl(String left,String right) {
        try {var a=URI.create(left);var b=URI.create(right);return a.getHost()!=null && a.getHost().equalsIgnoreCase(b.getHost()) && Objects.equals(a.getScheme(),b.getScheme()) && a.getPort()==b.getPort() && a.getUserInfo()==null && b.getUserInfo()==null && a.getQuery()==null && b.getQuery()==null && a.getFragment()==null && b.getFragment()==null && normalizedPath(a).equals(normalizedPath(b));}catch(Exception e){return false;}
    }
    private static String normalizedPath(URI uri){return Objects.toString(uri.getPath(),"").replaceAll("/$","").replaceAll("\\.git$","");}
    public static BuildDefinition.Source match(Source chosen,BuildDefinition.Definition definition) {
        var matches=definition.sources().stream().filter(s->"OCI".equals(chosen.provider())?chosen.resourceId().equals(s.repositoryId()):sameUrl(chosen.url(),s.url())).toList();
        if(matches.size()!=1)throw Errors.SOURCE_MISMATCH.exception();return matches.getFirst();
    }
}
