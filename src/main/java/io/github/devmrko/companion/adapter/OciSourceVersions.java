package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.SourceVersions;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.Source;
import io.github.devmrko.companion.service.Errors;
import com.oracle.bmc.devops.requests.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
@Component
public class OciSourceVersions implements SourceVersions {
    private final OciBuildHistory clients;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final io.github.devmrko.companion.port.GitCredentials credentials;
    public OciSourceVersions(OciBuildHistory clients){this(clients,new ConfiguredGitCredentials("", ""));}
    @org.springframework.beans.factory.annotation.Autowired
    public OciSourceVersions(OciBuildHistory clients,io.github.devmrko.companion.port.GitCredentials credentials){this.clients=clients;this.credentials=credentials;}
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    static String api(String url) {
        try {
            URI uri=URI.create(url);String path=uri.getPath();
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || uri.getPort()!=-1 || path==null || !path.matches("/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)+/?"))throw Errors.INVALID.exception();
            path=path.replaceAll("/$","").replaceAll("\\.git$","");
            if(Arrays.stream(path.split("/")).anyMatch(s->s.equals(".")||s.equals("..")))throw Errors.INVALID.exception();
            if("github.com".equalsIgnoreCase(uri.getHost()) && path.split("/").length==3)return "https://api.github.com/repos"+path;
            if("gitlab.com".equalsIgnoreCase(uri.getHost()))return "https://gitlab.com/api/v4/projects/"+enc(path.substring(1))+"/repository";
            throw Errors.INVALID.exception();
        }catch(IllegalArgumentException e){throw Errors.INVALID.exception();}
    }
    private String sourceApi(Source source) {
        if(!"GITEA".equals(source.provider()))return api(source.url());
        var uri=URI.create(source.url());String path=uri.getPath().replaceAll("\\.git$","");
        if(!"https".equals(uri.getScheme())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!credentials.allowedHost(uri.getHost())||!path.matches("/[A-Za-z0-9_-][A-Za-z0-9_.-]*/[A-Za-z0-9_-][A-Za-z0-9_.-]*"))throw Errors.SOURCE_READ.exception();
        return "https://"+uri.getHost()+"/api/v1/repos"+path;
    }
    private JsonNode get(String url) {
        try {
            var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Accept","application/json").header("User-Agent","OCI-Companion");
            if(credentials.allowedHost(URI.create(url).getHost())){var auth=credentials.configured(URI.create(url).getHost());builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString((auth.username()+":"+auth.password()).getBytes(StandardCharsets.UTF_8)));}
            var request=builder.GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()) {if(response.statusCode()!=200)throw Errors.SOURCE_READ.exception();var bytes=body.readNBytes(2_000_001);if(bytes.length>2_000_000)throw Errors.SOURCE_READ.exception();return mapper.readTree(bytes);}
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw Errors.SOURCE_READ.exception();}catch(Exception e){throw Errors.SOURCE_READ.exception();}
    }
    private void scope(com.oracle.bmc.devops.DevopsClient client,String comp,Source source) {
        var repository=client.getRepository(GetRepositoryRequest.builder().repositoryId(source.resourceId()).build()).getRepository();
        if(!comp.isEmpty() && !comp.equals(repository.getCompartmentId()))throw Errors.SCOPE.exception();
    }
    public List<Ref> refs(Connection c,String region,String comp,Source source) {
        if("OCI".equals(source.provider()))try(var client=clients.client(c,region)) {
            scope(client,comp,source);var result=new ArrayList<Ref>();
            for(var type:List.of(ListRefsRequest.RefType.Branch,ListRefsRequest.RefType.Tag)) {
                var refs=client.listRefs(ListRefsRequest.builder().repositoryId(source.resourceId()).refType(type).limit(100).build()).getRepositoryRefCollection().getItems();
                for(var ref:refs)result.add(new Ref(type==ListRefsRequest.RefType.Branch?"BRANCH":"TAG",ref.getRefName(),""));
            }return result;
        }catch(Exception e){throw Errors.SOURCE_READ.exception();}
        String base=sourceApi(source);boolean github=base.startsWith("https://api.github.com/");boolean gitea="GITEA".equals(source.provider());var result=new ArrayList<Ref>();
        for(var kind:List.of("branches","tags"))for(var item:get(base+"/"+kind+"?per_page=100&limit=100")) {
            var sha=item.path("commit").path(gitea&&kind.equals("tags")?"sha":github?"sha":"id").asString("");
            if(sha.matches("[0-9a-f]{40}"))result.add(new Ref(kind.equals("branches")?"BRANCH":"TAG",item.path("name").asString(),sha));
        }return result;
    }
    public List<Ref> commits(Connection c,String region,String comp,Source source,String ref) {
        if(ref.isBlank() || ref.length()>255)throw Errors.INVALID.exception();
        if("OCI".equals(source.provider()))try(var client=clients.client(c,region)) {
            scope(client,comp,source);
            return client.listCommits(ListCommitsRequest.builder().repositoryId(source.resourceId()).refName(ref).limit(50).build()).getRepositoryCommitCollection().getItems().stream().map(x->new Ref("COMMIT",Objects.toString(x.getCommitMessage(),"").lines().findFirst().orElse(""),x.getCommitId())).toList();
        }catch(Exception e){throw Errors.SOURCE_READ.exception();}
        String base=sourceApi(source);boolean github=base.startsWith("https://api.github.com/");boolean gitea="GITEA".equals(source.provider());var result=new ArrayList<Ref>();
        for(var item:get(base+"/commits?per_page=50&limit=50&"+((github||gitea)?"sha=":"ref_name=")+enc(ref))) {
            String sha=item.path((github||gitea)?"sha":"id").asString("");
            if(sha.matches("[0-9a-f]{40}"))result.add(new Ref("COMMIT",((github||gitea)?item.path("commit").path("message"):item.path("title")).asString("").lines().findFirst().orElse(""),sha));
        }return result;
    }
    public boolean hasCommit(Connection c,String region,String comp,Source source,String commit) {
        if(!commit.matches("[0-9a-f]{40}"))return false;
        if("OCI".equals(source.provider()))try(var client=clients.client(c,region)){scope(client,comp,source);return commit.equals(client.getCommit(GetCommitRequest.builder().repositoryId(source.resourceId()).commitId(commit).build()).getRepositoryCommit().getCommitId());}catch(Exception e){return false;}
        String base=sourceApi(source);return commit.equals(get(base+("GITEA".equals(source.provider())?"/git/commits/":"/commits/")+commit).path((base.startsWith("https://api.github.com/")||"GITEA".equals(source.provider()))?"sha":"id").asString(""));
    }
}
