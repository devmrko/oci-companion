package io.github.devmrko.companion.adapter;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CreateBucketDetails;
import com.oracle.bmc.objectstorage.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ReportStorage;
import io.github.devmrko.companion.service.*;
import io.github.devmrko.foundation.core.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OciReportStorage implements ReportStorage {
    private final OciBuildHistory authentication;
    public OciReportStorage(OciBuildHistory authentication) { this.authentication=authentication; }
    private ObjectStorageClient client(Connection c,String region) throws Exception {
        var client=ObjectStorageClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(authentication.authentication(c));
        client.setRegion(region);return client;
    }
    private String namespace(ObjectStorageClient client) { return client.getNamespace(GetNamespaceRequest.builder().build()).getValue(); }
    private void check(ObjectStorageClient client,String ns,String compartment,String bucket) {
        var b=client.getBucket(GetBucketRequest.builder().namespaceName(ns).bucketName(bucket).build()).getBucket();
        if(!compartment.equals(b.getCompartmentId()))throw Errors.SCOPE.exception();
    }
    public Options options(Connection c,String region,String compartment) {
        try(var client=client(c,region)) {
            String ns=namespace(client);var names=new ArrayList<String>();
            for(var b:client.getPaginators().listBucketsRecordIterator(ListBucketsRequest.builder().namespaceName(ns).compartmentId(compartment).build()))names.add(b.getName());
            names.sort(String::compareTo);return new Options(ns,names);
        }catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public void createBucket(Connection c,String region,String compartment,String name) {
        if(!name.matches("[A-Za-z0-9_-]{1,256}"))throw Errors.INVALID.exception();
        try(var client=client(c,region)) {
            client.createBucket(CreateBucketRequest.builder().namespaceName(namespace(client)).createBucketDetails(CreateBucketDetails.builder().name(name).compartmentId(compartment).publicAccessType(CreateBucketDetails.PublicAccessType.NoPublicAccess).storageTier(CreateBucketDetails.StorageTier.Standard).build()).build());
        }catch(Exception e){throw Errors.ADMIN_UNKNOWN.exception();}
    }
    public List<String> objects(Connection c,String region,String compartment,String bucket,String prefix) {
        try(var client=client(c,region)) {
            String ns=namespace(client);check(client,ns,compartment,bucket);var result=new ArrayList<String>();String start=null;
            do {var page=client.listObjects(ListObjectsRequest.builder().namespaceName(ns).bucketName(bucket).prefix(prefix).start(start).limit(100).build()).getListObjects();
                for(var obj:page.getObjects())if(obj.getName().endsWith("/test-reports.tar.gz"))result.add(obj.getName());
                start=page.getNextStartWith();
            }while(start!=null);
            return result;
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public TestReports.Summary read(Connection c,String region,String compartment,String bucket,String object) {
        try(var client=client(c,region)) {
            String ns=namespace(client);check(client,ns,compartment,bucket);
            var response=client.getObject(GetObjectRequest.builder().namespaceName(ns).bucketName(bucket).objectName(object).build());
            try(var in=response.getInputStream()){return TestReports.read(in);}
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
}
