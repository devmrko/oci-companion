package io.github.devmrko.companion.service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PullRegistryCredentialsTest {
 byte[] input(String json){return Base64.getEncoder().encode(json.getBytes(StandardCharsets.UTF_8));}
 @Test void selectsExactHostAndClearsBuffersAndNeverPrintsCredentials(){
  byte[] encoded=input("{\"auths\":{\"https://ap-chuncheon-1.ocir.io/\":{\"auth\":\""+Base64.getEncoder().encodeToString("tenant/user:token-value".getBytes(StandardCharsets.UTF_8))+"\"},\"yny.ocir.io\":{\"username\":\"other\",\"password\":\"wrong\"}}}");
  var credential=PullRegistryCredentials.decode(encoded,"ap-chuncheon-1.ocir.io");
  assertEquals("tenant/user",credential.username());assertArrayEquals("token-value".toCharArray(),credential.password());
  assertFalse(credential.toString().contains("token-value"));assertFalse(credential.toString().contains("tenant/user"));
  assertArrayEquals(new byte[encoded.length],encoded);credential.close();assertArrayEquals(new char[11],credential.password());
 }
 @Test void mismatchedOrAmbiguousHostAndMalformedAuthenticationAreRejectedWithoutValues(){
  for(String json:List.of("{\"auths\":{\"yny.ocir.io\":{\"username\":\"u\",\"password\":\"sensitive\"}}}","{\"auths\":{\"ap-chuncheon-1.ocir.io\":{\"auth\":\"invalid-sensitive\"}}}","{\"auths\":{\"ap-chuncheon-1.ocir.io\":{},\"https://ap-chuncheon-1.ocir.io/\":{}}}")){
   byte[] encoded=input(json);var error=assertThrows(IllegalStateException.class,()->PullRegistryCredentials.decode(encoded,"ap-chuncheon-1.ocir.io"));assertFalse(error.getMessage().contains("sensitive"));assertArrayEquals(new byte[encoded.length],encoded);
  }
 }
 @Test void legacyBuildersHaveNoCredentialReference(){
  var b=new io.github.devmrko.companion.port.ArmBuilds.Builder("arm","host","user","/key");assertEquals("",b.secretEnvironment());assertEquals("PRIVATE_ENDPOINT",b.secretEndpoint());
 }
}
