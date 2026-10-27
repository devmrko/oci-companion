package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.artifacts.model.*;
import com.oracle.bmc.artifacts.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.Registry;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciRegistry implements Registry {
    private final OciBuildHistory clients;
    public OciRegistry(OciBuildHistory clients) {this.clients=clients;}
    ArtifactsClient client(Connection c,String region) throws java.io.IOException {
        var result=ArtifactsClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000)
            .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(clients.authentication(c));result.setRegion(region);return result;
    }
    static void kind(String kind) {if(!Set.of("GENERIC","CONTAINER").contains(kind))throw Errors.REGISTRY_INPUT.exception();}
    static String value(Object value) {return Objects.toString(value,"");}
    static String date(Date value) {return value==null?"":value.toInstant().toString();}
    public List<Repository> list(Connection c,String region,String comp,String kind) {
        kind(kind);
        try(var client=client(c,region)) {
            var result=new ArrayList<Repository>();
            if(kind.equals("GENERIC")) {
                for(var r:client.getPaginators().listRepositoriesRecordIterator(ListRepositoriesRequest.builder().compartmentId(comp).build()))
                    result.add(new Repository(r.getId(),kind,r.getDisplayName(),value(r.getDescription()),value(r.getLifecycleState()),Boolean.TRUE.equals(r.getIsImmutable()),false,""));
            } else {
                for(var r:client.getPaginators().listContainerRepositoriesRecordIterator(ListContainerRepositoriesRequest.builder().compartmentId(comp).build()))
                    result.add(new Repository(r.getId(),kind,r.getDisplayName(),"",value(r.getLifecycleState()),null,Boolean.TRUE.equals(r.getIsPublic()),""));
            }
            return result;
        } catch(BusinessException e) {throw e;} catch(Exception e) {throw Errors.CLOUD.exception();}
    }
    Repository read(ArtifactsClient client,String comp,String kind,String id) {
        kind(kind);
        if(kind.equals("GENERIC")) {
            var response=client.getRepository(GetRepositoryRequest.builder().repositoryId(id).build());var r=response.getRepository();
            if(!comp.equals(r.getCompartmentId()))throw Errors.SCOPE.exception();
            return new Repository(r.getId(),kind,r.getDisplayName(),value(r.getDescription()),value(r.getLifecycleState()),Boolean.TRUE.equals(r.getIsImmutable()),false,response.getEtag());
        }
        var response=client.getContainerRepository(GetContainerRepositoryRequest.builder().repositoryId(id).build());var r=response.getContainerRepository();
        if(!comp.equals(r.getCompartmentId()))throw Errors.SCOPE.exception();
        return new Repository(r.getId(),kind,r.getDisplayName(),r.getReadme()==null?"":value(r.getReadme().getContent()),value(r.getLifecycleState()),Boolean.TRUE.equals(r.getIsImmutable()),Boolean.TRUE.equals(r.getIsPublic()),response.getEtag());
    }
    public Repository read(Connection c,String region,String comp,String kind,String id) {
        try(var client=client(c,region)){return read(client,comp,kind,id);}
        catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public Page artifacts(Connection c,String region,String comp,String kind,String id,String page) {
        try(var client=client(c,region)) {
            read(client,comp,kind,id);
            if(kind.equals("GENERIC")) {
                var r=client.listGenericArtifacts(ListGenericArtifactsRequest.builder().compartmentId(comp).repositoryId(id).limit(100).page(page.isEmpty()?null:page).build());
                return new Page(r.getGenericArtifactCollection().getItems().stream().map(a->new Artifact(a.getId(),a.getArtifactPath(),a.getVersion(),a.getSha256(),value(a.getSizeInBytes()),value(a.getLifecycleState()),date(a.getTimeCreated()))).toList(),value(r.getOpcNextPage()));
            }
            var r=client.listContainerImages(ListContainerImagesRequest.builder().compartmentId(comp).repositoryId(id).limit(100).page(page.isEmpty()?null:page).build());
            return new Page(r.getContainerImageCollection().getItems().stream().map(a->new Artifact(a.getId(),a.getRepositoryName(),value(a.getVersion()),a.getDigest(),"—",value(a.getLifecycleState()),date(a.getTimeCreated()))).toList(),value(r.getOpcNextPage()));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    static void validate(String kind,String name,String description) {
        kind(kind);
        if(name==null || name.length()>255 || description==null || description.length()>4000 ||
           !(kind.equals("GENERIC")?name.matches("[A-Za-z_][A-Za-z0-9_-]{0,254}"):name.matches("[a-z0-9]+(?:[._/-][a-z0-9]+)*")))throw Errors.REGISTRY_INPUT.exception();
    }
    public String create(Connection c,String region,String comp,String kind,String name,String description,boolean immutable,String token) {
        validate(kind,name,description);
        try(var client=client(c,region)) {
            if(kind.equals("GENERIC"))return client.createRepository(CreateRepositoryRequest.builder().opcRetryToken(token).createRepositoryDetails(
                CreateGenericRepositoryDetails.builder().compartmentId(comp).displayName(name).description(description).isImmutable(immutable).build()).build()).getRepository().getId();
            return client.createContainerRepository(CreateContainerRepositoryRequest.builder().opcRetryToken(token).createContainerRepositoryDetails(
                CreateContainerRepositoryDetails.builder().compartmentId(comp).displayName(name).isPublic(false)
                    .readme(ContainerRepositoryReadme.builder().content(description).format(ContainerRepositoryReadme.Format.TextPlain).build()).build()).build()).getContainerRepository().getId();
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
    }
    public void update(Connection c,String region,String comp,String kind,String id,String etag,String name,String description) {
        validate(kind,name,description);
        try(var client=client(c,region)) {
            var current=read(client,comp,kind,id);
            if(etag==null || !etag.equals(current.etag()))throw Errors.BUILD_CHANGED.exception();
            if(kind.equals("GENERIC"))client.updateRepository(UpdateRepositoryRequest.builder().repositoryId(id).ifMatch(etag)
                .updateRepositoryDetails(UpdateGenericRepositoryDetails.builder().displayName(name).description(description).build()).build());
            else {
                if(!current.name().equals(name))throw Errors.REGISTRY_INPUT.exception();
                client.updateContainerRepository(UpdateContainerRepositoryRequest.builder().repositoryId(id).ifMatch(etag)
                    .updateContainerRepositoryDetails(UpdateContainerRepositoryDetails.builder().readme(ContainerRepositoryReadme.builder().content(description).format(ContainerRepositoryReadme.Format.TextPlain).build()).build()).build());
            }
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.BUILD_CHANGED.exception();}
    }
}
