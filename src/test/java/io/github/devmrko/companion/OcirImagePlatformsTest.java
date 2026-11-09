package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
class OcirImagePlatformsTest {
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static String digest(byte[] b)throws Exception{return "sha256:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    @Test void singleImageVerifiesBothDigestsAndReadsConfig()throws Exception{
        byte[] config=bytes("{\"os\":\"linux\",\"architecture\":\"amd64\"}");
        byte[] manifest=bytes("{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\",\"digest\":\""+digest(config)+"\"}}");
        var result=OcirImagePlatforms.decode(manifest,digest(manifest),d->{assertEquals(digest(config),d);return config;});
        assertTrue(result.complete());assertEquals("amd64",result.platforms().getFirst().architecture());
        assertThrows(IllegalArgumentException.class,()->OcirImagePlatforms.decode(manifest,"sha256:"+"0".repeat(64),d->config));
        assertThrows(IllegalArgumentException.class,()->OcirImagePlatforms.decode(manifest,digest(manifest),d->bytes("{}")));
    }
    @Test void indexSupportsMultiarchAndUnknownEntriesPreventNegativeConclusion()throws Exception{
        String a="{\"digest\":\"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"platform\":{\"os\":\"linux\",\"architecture\":\"amd64\"}}";
        String arm=a.replace("amd64","arm64");String prefix="{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.index.v1+json\",\"manifests\":[";
        byte[] multi=bytes(prefix+a+","+arm+"]}");var result=OcirImagePlatforms.decode(multi,digest(multi),d->{fail("Index should not fetch blobs");return null;});assertTrue(result.complete());assertEquals(2,result.platforms().size());
        byte[] unknown=bytes(prefix+a+",{}]}");assertFalse(OcirImagePlatforms.decode(unknown,digest(unknown),d->null).complete());
    }
    @Test void untrustedConfigPathIsNeverRequested()throws Exception{
        byte[] manifest=bytes("{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\",\"digest\":\"https://evil.invalid/\"}}");
        assertFalse(OcirImagePlatforms.decode(manifest,digest(manifest),d->{fail("No unvalidated URL");return null;}).complete());
    }
    @Test void responseBodyLimitCancelsSubscription(){
        var subscriber=new OcirImagePlatforms.BoundedBody();var sub=org.mockito.Mockito.mock(java.util.concurrent.Flow.Subscription.class);subscriber.onSubscribe(sub);
        subscriber.onNext(java.util.List.of(java.nio.ByteBuffer.allocate(OcirImagePlatforms.LIMIT+1)));assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());org.mockito.Mockito.verify(sub).cancel();
    }
}
