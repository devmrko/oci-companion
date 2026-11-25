package io.github.devmrko.companion.service;

import io.github.devmrko.companion.port.RegistryCredentials;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Select only the exact requested registry; never fall back to another host's credentials. */
public final class PullRegistryCredentials {
 private PullRegistryCredentials(){}
 public static RegistryCredentials decode(byte[] encoded,String host){
  byte[] decoded=null,authBytes=null;char[] password=null;
  try {
   if(encoded.length>65536||!host.matches("[a-z0-9.-]+\\.ocir\\.io"))throw new IllegalArgumentException();
   decoded=Base64.getDecoder().decode(encoded);
   var auths=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(decoded).path("auths");
   if(!auths.isObject()||auths.size()>32)throw new IllegalArgumentException();
   com.fasterxml.jackson.databind.JsonNode selected=null;
   for(var entries=auths.fields();entries.hasNext();){var e=entries.next();String key=e.getKey();if(key.startsWith("https://"))key=key.substring(8);if(key.endsWith("/"))key=key.substring(0,key.length()-1);if(host.equalsIgnoreCase(key)){if(selected!=null)throw new IllegalArgumentException();selected=e.getValue();}}
   if(selected==null)throw new IllegalArgumentException();
   String username;
   if(selected.hasNonNull("auth")){
    authBytes=Base64.getDecoder().decode(selected.path("auth").asText());String auth=new String(authBytes,StandardCharsets.UTF_8);int split=auth.indexOf(':');if(split<1)throw new IllegalArgumentException();username=auth.substring(0,split);password=auth.substring(split+1).toCharArray();
   }else{username=selected.path("username").asText();password=selected.path("password").asText().toCharArray();}
   if(username.isBlank()||username.length()>512||username.chars().anyMatch(Character::isISOControl)||password.length==0||password.length>8192)throw new IllegalArgumentException();
   for(char ch:password)if(Character.isISOControl(ch))throw new IllegalArgumentException();
   return new RegistryCredentials(username,password);
  }catch(Exception e){throw new IllegalStateException("선택한 pull Secret의 OCIR 호스트·인증 형식을 확인하세요. 인증값은 표시하지 않습니다.");}
  finally{Arrays.fill(encoded,(byte)0);if(decoded!=null)Arrays.fill(decoded,(byte)0);if(authBytes!=null)Arrays.fill(authBytes,(byte)0);if(password!=null)Arrays.fill(password,'\0');}
 }
}
