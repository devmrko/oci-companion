package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** UI-managed bindings are separate from administrator-supplied kubeconfigs. */
@Component
public class OkeConnectionStore {
 private final Path root;
 private final JsonMapper json=JsonMapper.builder().build();
 public OkeConnectionStore(@Value("${companion.data-dir:.data/catalog}") String root){this.root=Path.of(root).toAbsolutePath().resolve("oke-connections");}
 private Path path(Connection c,String r,String comp,String id)throws Exception{
  String key=json.writeValueAsString(List.of(c.profile(),c.tenancy(),c.user(),r,comp,id));
  return root.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))+".json");
 }
 public OkeWorkloadHealth.Target get(Connection c,String r,String comp,String id){try{
  var p=path(c,r,comp,id);if(!Files.exists(p))return null;
  if(Files.isSymbolicLink(root)||Files.isSymbolicLink(p)||Files.size(p)>16384)throw new IllegalArgumentException();
  var t=json.readValue(Files.readString(p),OkeWorkloadHealth.Target.class);
  if(!id.equals(t.appId())||!c.profile().equals(t.profile())||!c.tenancy().equals(t.tenancy())||!c.user().equals(t.user())||!r.equals(t.region())||!comp.equals(t.compartment()))throw new IllegalArgumentException();
  return t;
 }catch(Exception e){throw new IllegalArgumentException("OKE 조회 연결 설정을 읽지 못했습니다.");}}
 public synchronized void remove(Connection c,String r,String comp,String id)throws Exception{
  get(c,r,comp,id);var p=path(c,r,comp,id);if(Files.isSymbolicLink(root)||Files.isSymbolicLink(p))throw new IllegalArgumentException();Files.deleteIfExists(p);
 }
 public synchronized void save(Connection c,String r,String comp,String id,OkeWorkloadHealth.Target t)throws Exception{
  if(!id.equals(t.appId())||!c.profile().equals(t.profile())||!c.tenancy().equals(t.tenancy())||!c.user().equals(t.user())||!r.equals(t.region())||!comp.equals(t.compartment()))throw new IllegalArgumentException();
  Files.createDirectories(root);if(Files.isSymbolicLink(root))throw new IllegalArgumentException();
  var p=path(c,r,comp,id);if(Files.isSymbolicLink(p))throw new IllegalArgumentException();
  var tmp=Files.createTempFile(root,"binding-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
  try{Files.writeString(tmp,json.writeValueAsString(t));Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}
 }
}
