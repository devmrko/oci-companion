package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.*;
import java.net.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OkeEndpointChecksTest {
 @Test void deniesPrivateMetadataTransitionAndReservedAddresses()throws Exception{
  for(String s:List.of("127.0.0.1","10.0.0.1","169.254.169.254","100.64.46.23","192.168.1.1","172.31.1.1","0.0.0.0","192.0.2.1","198.18.0.1","203.0.113.1","224.0.0.1","::1","fc00::1","fe80::1","2001:db8::1","2001::1","2001:20::1","2002:7f00:1::1"))assertFalse(OciOkeEndpointChecks.publicAddress(InetAddress.getByName(s)),s);
  assertTrue(OciOkeEndpointChecks.publicAddress(InetAddress.getByName("8.8.8.8")));assertTrue(OciOkeEndpointChecks.publicAddress(InetAddress.getByName("2001:4860:4860::8888")));assertTrue(OciOkeEndpointChecks.publicAddress(InetAddress.getByName("2606:4700:4700::1111")));
 }
 @Test void rejectsArbitraryUrlsHeaderInjectionAndWildcardHosts(){for(String h:List.of("localhost","*.example.com","example.com:443","127.0.0.1","x\r\nHost: evil","https://example.com"))assertFalse(OciOkeEndpointChecks.validHost(h));for(String p:List.of("//evil","/\r\nHost: evil","/a?token=secret","/a#b","/a\\b"))assertFalse(OciOkeEndpointChecks.validPath(p));assertTrue(OciOkeEndpointChecks.validHost("hello.example.com"));assertTrue(OciOkeEndpointChecks.validPath("/health/ready"));}
 @Test void statusLineIsBoundedAndDoesNotReadResponseBody()throws Exception{var input=new ByteArrayInputStream("HTTP/1.1 302 Found\r\nsecret body".getBytes());assertEquals(302,OciOkeEndpointChecks.status(input));assertEquals('s',input.read());assertThrows(IOException.class,()->OciOkeEndpointChecks.status(new ByteArrayInputStream("x".repeat(300).getBytes())));assertThrows(IOException.class,()->OciOkeEndpointChecks.status(new ByteArrayInputStream("NOT HTTP\n".getBytes())));}
 @Test void onlyExactCurrentIngressRouteCanBeProbed(){var resources=mock(OkeRuntimeResources.class);when(resources.read(any(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(new OkeRuntimeResources.Snapshot(Instant.now(),"cluster","ns",Map.of("ingresses",new OkeRuntimeResources.Listing(true,List.of(),"")),List.of(new OkeRuntimeResources.Ingress("web","nginx",List.of(new OkeRuntimeResources.Route("example.com","/health","hello","8080")),List.of(),List.of())),List.of()));var service=new OciOkeEndpointChecks(resources);assertThrows(RuntimeException.class,()->service.inspect(null,"r","c","e","PUBLIC_ENDPOINT","ns","web","evil.example.com","/health"));assertThrows(RuntimeException.class,()->service.inspect(null,"r","c","e","PUBLIC_ENDPOINT","ns","web","example.com","/admin"));}
 @Test void failedInventoryIsUnknownNotSuccessful(){var resources=mock(OkeRuntimeResources.class);when(resources.read(any(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(new OkeRuntimeResources.Snapshot(Instant.now(),"c","ns",Map.of(),List.of(),List.of()));assertEquals("UNKNOWN",new OciOkeEndpointChecks(resources).inspect(null,"r","c","e","PUBLIC_ENDPOINT","ns","web","example.com","/").checks().getFirst().state());}
}
