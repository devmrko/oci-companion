package io.github.devmrko.companion.service;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.*;
import io.github.devmrko.companion.port.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class CatalogService {
    private final Object[] refreshLocks=java.util.stream.IntStream.range(0,64).mapToObj(i -> new Object()).toArray();
    private final ResourceCatalog cloud;
    private final CatalogStore store;
    public CatalogService(ResourceCatalog cloud,CatalogStore store) { this.cloud=cloud; this.store=store; }
    public String scope(Connection c,String region,String compartment) {
        return String.join("\n",c.demo()?"demo":"oci",c.profile(),c.tenancy(),c.user(),region,compartment);
    }
    public View read(Connection c,String region,String compartment) {
        try { return new View(store.read(scope(c,region,compartment)).orElse(null),null); }
        catch (IllegalStateException e) { return new View(null,e.getMessage()); }
    }
    public View refresh(Connection c,String region,String compartment) {
        synchronized(refreshLocks[Math.floorMod(scope(c,region,compartment).hashCode(),refreshLocks.length)]) {
        var previous=read(c,region,compartment);
        try {
            var resources=List.copyOf(cloud.resources(c,region,compartment));
            var snapshot=new Snapshot(1,scope(c,region,compartment),Instant.now().toString(),resources);
            store.write(snapshot); return new View(snapshot,null);
        } catch (RuntimeException e) {
            return new View(previous.snapshot(),"갱신 실패 · 이전 캐시 유지. 연결·권한·저장 경로 확인.");
        }
    }
    }
    private String externalScope(Connection c) {
        return String.join("\n",c.demo()?"demo":"oci",c.profile(),c.tenancy(),c.user())+"\nexternal-git";
    }
    public List<Source> sources(Connection c,String region,String compartment) {
        String destination=externalScope(c);
        store.migrateExternalSources(destination.substring(0,destination.lastIndexOf('\n')+1),destination);
        var result=new ArrayList<>(store.sources(scope(c,region,compartment)).items());
        result.addAll(store.sources(destination).items());return List.copyOf(result);
    }
    public void register(Connection c,String region,String compartment,String provider,String name,String url,String branch,String resourceId) {
        if (name==null || name.isBlank() || name.length()>128 || branch==null || branch.isBlank() || branch.length()>255 || branch.chars().anyMatch(Character::isISOControl)) throw Errors.INVALID.exception();
        if ("OCI".equals(provider)) {
            var snapshot=read(c,region,compartment).snapshot();
            if (snapshot==null) throw Errors.MISSING.exception();
            String selectedId=resourceId;
            var resource=snapshot.resources().stream().filter(r -> r.kind().equals("REPOSITORY") && r.id().equals(selectedId)).findFirst().orElseThrow(Errors.MISSING::exception);
            url=resource.url(); name=resource.name();
        } else {
            if (!Set.of("GITHUB","GITLAB","GITEA").contains(provider)) throw Errors.INVALID.exception();
            try {
                var uri=URI.create(url);
                if (url.length()>2048 || !"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || uri.getPort()!=-1 || uri.getPath()==null || uri.getPath().split("/").length<3) throw Errors.INVALID.exception();
                if (provider.equals("GITHUB") && !uri.getHost().equalsIgnoreCase("github.com")) throw Errors.INVALID.exception();
            } catch (IllegalArgumentException e) { throw Errors.INVALID.exception(); }
            resourceId="";
        }
        store.saveSource(provider.equals("OCI")?scope(c,region,compartment):externalScope(c),new Source(UUID.randomUUID().toString(),provider,name,url,branch,resourceId));
    }
    public void remove(Connection c,String region,String compartment,String id) { store.deleteSource(scope(c,region,compartment),id); store.deleteSource(externalScope(c),id); }
}
