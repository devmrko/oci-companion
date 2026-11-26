package io.github.devmrko.companion;
import io.github.devmrko.companion.service.PullRegistryHosts;
import io.github.devmrko.companion.port.OkeRuntimeResources.PullRegistry;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PullRegistryHostsTest {
 @Test void extractsOnlyHostsAndClearsCredentialBuffer()throws Exception {
  byte[] bytes=Base64.getEncoder().encode("{\"auths\":{\"https://ap-chuncheon-1.ocir.io/\":{\"auth\":\"private-value\"}}}".getBytes(StandardCharsets.UTF_8));
  assertEquals(List.of("ap-chuncheon-1.ocir.io"),PullRegistryHosts.decode(bytes));assertArrayEquals(new byte[bytes.length],bytes);
 }
 @Test void autoUsesSelectedSecretAndAllowsExplicitFormats(){var secret=new PullRegistry(true,List.of("ap-chuncheon-1.ocir.io"),"");assertEquals("ap-chuncheon-1.ocir.io",PullRegistryHosts.choose("AUTO","ap-chuncheon-1","yny",secret));assertEquals("yny.ocir.io",PullRegistryHosts.choose("REGION_KEY","ap-chuncheon-1","yny",secret));assertEquals("ap-chuncheon-1.ocir.io",PullRegistryHosts.choose("REGION_NAME","ap-chuncheon-1","yny",null));}
 @Test void cannotRedirectImageToUnrelatedSecretHost(){assertEquals("yny.ocir.io",PullRegistryHosts.choose("AUTO","ap-chuncheon-1","yny",new PullRegistry(true,List.of("other.example"),"")));assertThrows(RuntimeException.class,()->PullRegistryHosts.choose("other.example","ap-chuncheon-1","yny",null));}
 @Test void rejectsInvalidOrOversizedPayloadWithoutEchoingIt(){assertThrows(Exception.class,()->PullRegistryHosts.decode(new byte[65537]));assertThrows(Exception.class,()->PullRegistryHosts.decode(Base64.getEncoder().encode("not-json".getBytes(StandardCharsets.UTF_8))));}
}
