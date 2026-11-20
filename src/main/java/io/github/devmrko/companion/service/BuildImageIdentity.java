package io.github.devmrko.companion.service;

import io.github.devmrko.companion.port.BuildHistory;
import java.util.*;
import java.util.regex.Pattern;

/** Uses only the selected run's delivered artifact record, never the current tag target. */
public final class BuildImageIdentity {
 private BuildImageIdentity() {}
 private static final Pattern DIGEST=Pattern.compile("sha256:[a-fA-F0-9]{64}");
 private static final Pattern ADDRESS=Pattern.compile("[a-z0-9.-]+/[a-z0-9]+/[a-z0-9]+(?:[._/-][a-z0-9]+)*");
 public record Image(String name,String reference,String note) { public boolean verified(){return !reference.isBlank();} }
 public static List<Image> inspect(BuildHistory.Detail detail){
  boolean success="SUCCEEDED".equals(detail.state())&&"SUCCEEDED".equals(detail.buildState())&&"SUCCEEDED".equals(detail.storageState());
  return detail.artifacts().stream().filter(a->"Container Registry".equals(a.kind())).map(a->{
   String reference=success?reference(a.location(),a.version()):"";
   return new Image(a.name(),reference,reference.isEmpty()?"실행 기록의 이미지 주소·digest 확인 불가":"OCI 전달 기록 · digest 고정");
  }).toList();
 }
 public static String reference(String uri,String hash){
  if(uri==null||hash==null||!DIGEST.matcher(hash).matches())return "";
  String digest=hash.toLowerCase(Locale.ROOT),address=uri;
  int at=uri.indexOf('@');
  if(at>=0){if(!uri.substring(at+1).equalsIgnoreCase(digest))return "";address=uri.substring(0,at);}
  int colon=address.lastIndexOf(':');
  if(colon>address.lastIndexOf('/')){
   if(!address.substring(colon+1).matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}"))return "";
   address=address.substring(0,colon);
  }
  if(!ADDRESS.matcher(address).matches())return "";
  return address+"@"+digest;
 }
}
