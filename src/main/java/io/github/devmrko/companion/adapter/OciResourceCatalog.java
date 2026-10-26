package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.*;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.*;
import io.github.devmrko.companion.port.ResourceCatalog;
import io.github.devmrko.companion.service.Errors;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component @Profile("!demo")
public class OciResourceCatalog implements ResourceCatalog {
    private final String configPath;
    private static final ClientConfiguration CONFIG=ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();
    public OciResourceCatalog(@Value("${companion.oci-config}") String path) { configPath=path; }
    private ConfigFileAuthenticationDetailsProvider auth(Connection c) throws java.io.IOException {
        var auth=new ConfigFileAuthenticationDetailsProvider(configPath,c.profile());
        if (!auth.getTenantId().equals(c.tenancy()) || !auth.getUserId().equals(c.user())) throw Errors.SCOPE.exception();
        return auth;
    }
    public List<Choice> regions(Connection c) {
        try (var client=IdentityClient.builder().configuration(CONFIG).build(auth(c))) {
            return client.listRegionSubscriptions(ListRegionSubscriptionsRequest.builder().tenancyId(c.tenancy()).build()).getItems().stream()
                .map(r -> new Choice(r.getRegionName(),r.getRegionName())).toList();
        } catch (Exception e) { throw Errors.CLOUD.exception(); }
    }
    public List<Choice> compartments(Connection c) {
        try (var client=IdentityClient.builder().configuration(CONFIG).build(auth(c))) {
            var result=new ArrayList<Choice>(); result.add(new Choice(c.tenancy(),"테넌시"));
            for (var item:client.getPaginators().listCompartmentsRecordIterator(ListCompartmentsRequest.builder().compartmentId(c.tenancy())
                .compartmentIdInSubtree(true).accessLevel(ListCompartmentsRequest.AccessLevel.Accessible).build())) {
                if (item.getLifecycleState()==com.oracle.bmc.identity.model.Compartment.LifecycleState.Active)
                    result.add(new Choice(item.getId(),item.getName(),item.getCompartmentId()));
            }
            return result;
        } catch (Exception e) { throw Errors.CLOUD.exception(); }
    }
    public List<Resource> resources(Connection c,String region,String compartment) {
        try (var client=DevopsClient.builder().configuration(CONFIG).build(auth(c))) {
            client.setRegion(region);
            var result=new ArrayList<Resource>();
            for (var r:client.getPaginators().listProjectsRecordIterator(ListProjectsRequest.builder().compartmentId(compartment).build()))
                result.add(new Resource(r.getId(),r.getName(),"PROJECT",String.valueOf(r.getLifecycleState()),""));
            for (var r:client.getPaginators().listRepositoriesRecordIterator(ListRepositoriesRequest.builder().compartmentId(compartment).build()))
                result.add(new Resource(r.getId(),r.getName(),"REPOSITORY",String.valueOf(r.getLifecycleState()),r.getHttpUrl(),r.getProjectId()));
            for (var r:client.getPaginators().listBuildPipelinesRecordIterator(ListBuildPipelinesRequest.builder().compartmentId(compartment).build()))
                result.add(new Resource(r.getId(),r.getDisplayName(),"BUILD",String.valueOf(r.getLifecycleState()),"",r.getProjectId()));
            for (var r:client.getPaginators().listDeployPipelinesRecordIterator(ListDeployPipelinesRequest.builder().compartmentId(compartment).build()))
                result.add(new Resource(r.getId(),r.getDisplayName(),"DEPLOY",String.valueOf(r.getLifecycleState()),"",r.getProjectId()));
            return result;
        } catch (Exception e) { throw Errors.CLOUD.exception(); }
    }
}
