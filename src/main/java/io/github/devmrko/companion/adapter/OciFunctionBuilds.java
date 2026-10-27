package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.functions.*;
import com.oracle.bmc.functions.requests.*;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.FunctionBuilds;
import io.github.devmrko.companion.service.Errors;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OciFunctionBuilds implements FunctionBuilds {
    // Functions signs the payload; SDK 3.x requires a separate stream for signing.
    static final class SignedBody extends ByteArrayInputStream implements com.oracle.bmc.http.client.io.DuplicatableInputStream {
        SignedBody(byte[] bytes) { super(bytes); }
        public InputStream duplicate() { return new SignedBody(buf.clone()); }
    }
    private final OciBuildHistory authentication;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public OciFunctionBuilds(OciBuildHistory authentication) { this.authentication=authentication; }
    private ClientConfiguration configuration() { return ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build(); }
    FunctionsManagementClient client(Connection c,String region) throws IOException {
        var client=FunctionsManagementClient.builder().configuration(configuration()).build(authentication.authentication(c));client.setRegion(region);return client;
    }
    private ObjectStorageClient storage(Connection c,String region) throws IOException {
        var client=ObjectStorageClient.builder().configuration(configuration()).build(authentication.authentication(c));client.setRegion(region);return client;
    }
    public Storage storageOptions(Connection c,String region,String compartment) {
        try(var client=storage(c,region)) {
            var namespace=client.getNamespace(GetNamespaceRequest.builder().build()).getValue();var buckets=new ArrayList<String>();
            for(var b:client.getPaginators().listBucketsRecordIterator(ListBucketsRequest.builder().namespaceName(namespace).compartmentId(compartment).build()))buckets.add(b.getName());
            buckets.sort(String::compareTo);return new Storage(namespace,buckets);
        }catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public List<Subnet> subnets(Connection c,String region,String compartment) {
        try(var client=com.oracle.bmc.core.VirtualNetworkClient.builder().configuration(configuration()).build(authentication.authentication(c))) {
            client.setRegion(region);var result=new ArrayList<Subnet>();
            for(var s:client.getPaginators().listSubnetsRecordIterator(com.oracle.bmc.core.requests.ListSubnetsRequest.builder().compartmentId(compartment).build()))
                if(s.getLifecycleState()==com.oracle.bmc.core.model.Subnet.LifecycleState.Available)result.add(new Subnet(s.getId(),s.getDisplayName(),s.getCidrBlock()));
            result.sort(Comparator.comparing(Subnet::name));return result;
        }catch(Exception e){throw Errors.CLOUD.exception();}
    }
    private com.oracle.bmc.functions.model.Application application(FunctionsManagementClient client,String compartment,String id) {
        var app=client.getApplication(GetApplicationRequest.builder().applicationId(id).build()).getApplication();
        if(!compartment.equals(app.getCompartmentId()))throw Errors.SCOPE.exception();return app;
    }
    public List<Application> applications(Connection c,String region,String compartment) {
        try(var client=client(c,region)) {
            var result=new ArrayList<Application>();
            for(var a:client.getPaginators().listApplicationsRecordIterator(ListApplicationsRequest.builder().compartmentId(compartment).build()))
                result.add(new Application(a.getId(),a.getDisplayName(),a.getLifecycleState().getValue(),String.valueOf(a.getShape())));
            result.sort(Comparator.comparing(Application::name));return result;
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    private Builder read(FunctionsManagementClient client,String compartment,String app,String id) {
        var a=application(client,compartment,app);
        var response=client.getFunction(GetFunctionRequest.builder().functionId(id).build());var f=response.getFunction();
        if(!compartment.equals(f.getCompartmentId()) || !app.equals(f.getApplicationId()))throw Errors.SCOPE.exception();
        var config=new HashMap<String,String>();if(a.getConfig()!=null)config.putAll(a.getConfig());if(f.getConfig()!=null)config.putAll(f.getConfig());
        return new Builder(f.getId(),f.getDisplayName(),f.getLifecycleState().getValue(),Objects.toString(f.getImage(),""),
            f.getMemoryInMBs(),f.getDetachedModeTimeoutInSeconds()==null?f.getTimeoutInSeconds():f.getDetachedModeTimeoutInSeconds(),
            config.getOrDefault("BUILD_RUNNER_CONTRACT",""),config.getOrDefault("BUILD_NAMESPACE",""),config.getOrDefault("BUILD_BUCKET",""),config.getOrDefault("BUILD_PREFIX",""),response.getEtag()+"/"+Objects.toString(a.getTimeUpdated(),""));
    }
    public List<Builder> builders(Connection c,String region,String compartment,String app) {
        try(var client=client(c,region)) {
            application(client,compartment,app);var result=new ArrayList<Builder>();
            for(var f:client.getPaginators().listFunctionsRecordIterator(ListFunctionsRequest.builder().applicationId(app).build()))result.add(read(client,compartment,app,f.getId()));
            result.sort(Comparator.comparing(Builder::name));return result;
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public Builder read(Connection c,String region,String compartment,String app,String id) {
        try(var client=client(c,region)) { return read(client,compartment,app,id); }
        catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public void invoke(Connection c,String region,String compartment,String app,String id,String etag,String payload) {
        try(var management=client(c,region)) {
            var f=read(management,compartment,app,id);
            if(!f.compatible() || !"ACTIVE".equals(f.state()) || !Objects.equals(etag,f.etag()))throw Errors.BUILD_CHANGED.exception();
            var current=management.getFunction(GetFunctionRequest.builder().functionId(id).build());
            if(!compartment.equals(current.getFunction().getCompartmentId()) || !app.equals(current.getFunction().getApplicationId()) || !f.etag().startsWith(current.getEtag()+"/"))throw Errors.BUILD_CHANGED.exception();
            var endpoint=current.getFunction().getInvokeEndpoint();
            var invokeConfig=ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(310000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();
            try(var invoke=FunctionsInvokeClient.builder().configuration(invokeConfig).endpoint(endpoint).build(authentication.authentication(c))) {
                var response=invoke.invokeFunction(InvokeFunctionRequest.builder().functionId(id).fnInvokeType(InvokeFunctionRequest.FnInvokeType.Detached)
                    .invokeFunctionBody(new SignedBody(payload.getBytes(StandardCharsets.UTF_8))).build());
                if(response.getInputStream()!=null)response.getInputStream().close();
                if(response.get__httpStatusCode__()!=202)throw Errors.BUILD_UNKNOWN.exception();
            }
        } catch(io.github.devmrko.foundation.core.error.BusinessException e) { throw e; }
        catch(Exception e) {
            Throwable cause=e;for(int i=0;i<10 && cause.getCause()!=null;i++)cause=cause.getCause();
            String message=Objects.toString(cause.getMessage(),"");
            org.slf4j.LoggerFactory.getLogger(OciFunctionBuilds.class).warn("Function invocation failed: {}: {}",cause.getClass().getSimpleName(),message.substring(0,Math.min(512,message.length())));
            throw Errors.BUILD_UNKNOWN.exception();
        }
    }
    private Builder ready(Connection c,String r,String comp,String app,String f) { var b=read(c,r,comp,app,f);if(!b.compatible())throw Errors.INVALID.exception();return b; }
    public Runs runs(Connection c,String r,String comp,String app,String f,String start) {
        var b=ready(c,r,comp,app,f);
        if(start.length()>1024 || (!start.isEmpty() && !start.startsWith(b.prefix()+"/")))throw Errors.INVALID.exception();
        try(var storage=storage(c,r)) {
            var list=storage.listObjects(ListObjectsRequest.builder().namespaceName(b.namespace()).bucketName(b.bucket()).prefix(b.prefix()+"/").start(start.isEmpty()?null:start).fields("name,timeCreated").limit(200).build()).getListObjects();
            var result=new ArrayList<Run>();
            for(var o:list.getObjects()) if(o.getName().endsWith("/result.json")) {
                var id=o.getName().substring(b.prefix().length()+1).split("/")[0];
                if(!id.matches("[0-9a-f-]{36}"))continue;
                var response=storage.getObject(GetObjectRequest.builder().namespaceName(b.namespace()).bucketName(b.bucket()).objectName(o.getName()).build());
                try(var in=response.getInputStream()) {
                    var text=new String(in.readNBytes(65536),StandardCharsets.UTF_8);var json=mapper.readTree(text);
                    result.add(new Run(id,json.path("status").asString("UNKNOWN"),text,o.getTimeCreated()==null?"":o.getTimeCreated().toInstant().toString()));
                }
            }
            result.sort(Comparator.comparing(Run::createdAt).reversed());return new Runs(result,Objects.toString(list.getNextStartWith(),""));
        } catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
    public String log(Connection c,String r,String comp,String app,String f,String run) {
        if(!run.matches("[0-9a-f-]{36}"))throw Errors.INVALID.exception();var b=ready(c,r,comp,app,f);
        try(var storage=storage(c,r)) {
            var response=storage.getObject(GetObjectRequest.builder().namespaceName(b.namespace()).bucketName(b.bucket()).objectName(b.prefix()+"/"+run+"/build.log").build());
            try(var in=response.getInputStream()) { var bytes=in.readNBytes(200001);return new String(bytes,0,Math.min(bytes.length,200000),StandardCharsets.UTF_8)+(bytes.length>200000?"\n[200KB까지 표시 · 전체 로그는 Object Storage에서 확인]":""); }
        } catch(com.oracle.bmc.model.BmcException e) { if(e.getStatusCode()==404)return "로그 기록 대기 · 계속 없으면 Function 호출 로그와 버킷 권한을 확인하세요.";throw Errors.CLOUD.exception(); }
        catch(Exception e) { throw Errors.CLOUD.exception(); }
    }
}
