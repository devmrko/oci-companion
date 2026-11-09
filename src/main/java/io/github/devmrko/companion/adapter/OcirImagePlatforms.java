package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.containerregistry.ContainerRegistryClient;
import com.oracle.bmc.containerregistry.requests.GetAccessTokenRequest;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ImagePlatforms;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

@Component
public class OcirImagePlatforms implements ImagePlatforms {
    private static final String ACCEPT="application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json";
    static final int LIMIT=1048576;
    private final OciBuildHistory clients;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public OcirImagePlatforms(OciBuildHistory clients){this.clients=clients;}
    public Result read(Connection c,String region,String image){
        if(c.demo())return Result.unknown();
        try {
            var ref=OciDeploymentImages.parse(region,image);if(ref==null)return Result.unknown();
            try(var client=ContainerRegistryClient.builder().configuration(ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(10000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build()).build(clients.authentication(c))){
                client.setRegion(region);URI endpoint=URI.create(client.getEndpoint());
                String repository=ref.namespace()+"/"+ref.repository();
                String token=client.getAccessToken(GetAccessTokenRequest.builder().service(endpoint.getHost()).scope("repository:"+repository+":pull").build()).getAccessToken().getToken();
                if(token==null||token.isBlank())return Result.unknown();
                byte[] manifest=get(endpoint.resolve("/v2/"+repository+"/manifests/"+ref.digest()),token);
                return decode(manifest,ref.digest(),digest->get(endpoint.resolve("/v2/"+repository+"/blobs/"+digest),token));
            }
        }catch(Exception e){return Result.unknown();}
    }
    @FunctionalInterface interface BlobReader {byte[] read(String digest)throws Exception;}
    static JsonNode checked(byte[] bytes,String digest)throws Exception{
        if(bytes.length>LIMIT||!digest.matches("sha256:[a-f0-9]{64}")||!digest.equals("sha256:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))throw new IllegalArgumentException("digest mismatch");
        return JsonMapper.builder().build().readTree(bytes);
    }
    static Result decode(byte[] bytes,String digest,BlobReader blobs)throws Exception{
        var root=checked(bytes,digest);if(root.path("schemaVersion").asInt()!=2)return Result.unknown();
        String media=root.path("mediaType").asText("");var platforms=new LinkedHashSet<Platform>();boolean complete=true;
        if(Set.of("application/vnd.oci.image.index.v1+json","application/vnd.docker.distribution.manifest.list.v2+json").contains(media)){
            var manifests=root.path("manifests");if(!manifests.isArray()||manifests.size()>100)return Result.unknown();
            for(var entry:manifests){
                if("attestation-manifest".equals(entry.path("annotations").path("vnd.docker.reference.type").asText("")))continue;
                var p=entry.path("platform");String os=p.path("os").asText(""),arch=p.path("architecture").asText("");
                if(!entry.path("digest").asText("").matches("sha256:[a-f0-9]{64}")||!Set.of("application/vnd.oci.image.manifest.v1+json","application/vnd.docker.distribution.manifest.v2+json").contains(entry.path("mediaType").asText(""))||!os.matches("[a-z0-9_]{1,32}")||!Set.of("amd64","arm64").contains(arch)){complete=false;continue;}
                platforms.add(new Platform(os,arch));
            }
        }else if(Set.of("application/vnd.oci.image.manifest.v1+json","application/vnd.docker.distribution.manifest.v2+json").contains(media)){
            if(!Set.of("application/vnd.oci.image.config.v1+json","application/vnd.docker.container.image.v1+json").contains(root.path("config").path("mediaType").asText("")))return Result.unknown();
            String configDigest=root.path("config").path("digest").asText("");
            if(!configDigest.matches("sha256:[a-f0-9]{64}"))return Result.unknown();
            var config=checked(blobs.read(configDigest),configDigest);String os=config.path("os").asText(""),arch=config.path("architecture").asText("");
            if(!os.matches("[a-z0-9_]{1,32}")||!Set.of("amd64","arm64").contains(arch))return Result.unknown();platforms.add(new Platform(os,arch));
        }else return Result.unknown();
        return new Result(List.copyOf(platforms),complete&&!platforms.isEmpty(),"digest 검증한 OCI/Docker manifest 메타데이터");
    }
    private byte[] get(URI uri,String token)throws Exception{
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Accept",ACCEPT).GET().build();
        var future=http.sendAsync(request,info->new BoundedBody());
        try{var response=future.get(15,TimeUnit.SECONDS);if(response.statusCode()!=200)throw new IllegalStateException("registry response");return response.body();}
        finally{future.cancel(true);}
    }
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
        public void onNext(List<ByteBuffer> chunks){for(var chunk:chunks){if(chunk.remaining()>LIMIT-bytes.size()){subscription.cancel();result.completeExceptionally(new IllegalStateException("metadata too large"));return;}byte[] data=new byte[chunk.remaining()];chunk.get(data);bytes.writeBytes(data);}subscription.request(1);}
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
