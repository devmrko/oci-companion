package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ArmBuilds;
import io.github.devmrko.companion.port.BuildSpecDraftStore;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** OS exclusion plus a persistent unresolved-run marker; never expire a remote operation by wall clock. */
final class BuildExecutionLocks {
 private final Path root;private final BuildSpecDraftStore store;
 private final JsonMapper json=JsonMapper.builder().build();
 BuildExecutionLocks(String directory,BuildSpecDraftStore store){root=Path.of(directory).resolve("build-locks");this.store=store;}
 static String physical(Connection c,String region,ArmBuilds.Builder b){return c.tenancy()+"\n"+region+"\n"+b.kind()+"\n"+(b.kind().equals("FUNCTION")?b.functionId():b.user()+"@"+b.host().toLowerCase(Locale.ROOT));}
 static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 Lease acquire(String physical,String runKey,boolean recover){
  FileChannel channel=null;FileLock lock=null;
  try{
   Files.createDirectories(root);Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"));String id=hash(physical);Path file=root.resolve(id+".lock"),marker=root.resolve(id+".json");
   channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE);Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
   try{lock=channel.tryLock();}catch(OverlappingFileLockException busy){throw new IllegalStateException("이 빌더는 실행 중입니다 · 기존 실행 결과를 확인한 뒤 다시 시작하세요");}
   if(lock==null)throw new IllegalStateException("이 빌더는 다른 Companion에서 실행 중입니다");
   String existing=Files.exists(marker)?Files.readString(marker):"";
   if(recover){if(!existing.equals(runKey))throw new IllegalStateException("이 실행의 빌더 잠금 기록이 일치하지 않습니다");}
   else {
    if(!existing.isBlank()){
     var prior=store.read(existing).map(d->json.readValue(d.content(),ArmBuilds.Run.class));
     if(prior.isEmpty()||!Set.of("SUCCEEDED","FAILED","ABANDONED").contains(prior.get().state()))throw new IllegalStateException("이 빌더에 완료 여부가 확인되지 않은 실행이 있습니다 · 기존 실행에서 결과를 재확인하세요");
    }
    Path temp=Files.createTempFile(root,".marker-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    try{Files.writeString(temp,runKey);Files.move(temp,marker,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
   }
   return new Lease(channel,lock,marker,runKey);
  }catch(Exception e){if(lock!=null)try{lock.release();}catch(Exception ignored){}if(channel!=null)try{channel.close();}catch(Exception ignored){}if(e instanceof IllegalStateException failure)throw failure;throw new IllegalStateException("빌더 잠금을 저장하지 못했습니다 · 실행하지 않았습니다",e);}
 }
 List<String> pending(String scope){
  if(!Files.isDirectory(root))return List.of();
  try(var paths=Files.list(root)){var result=new ArrayList<String>();for(Path p:paths.filter(x->x.getFileName().toString().endsWith(".json")).toList()){String value=Files.readString(p);if(value.startsWith(scope+"\n")){String id=value.substring(scope.length()+1);if(id.matches("[a-f0-9-]{36}"))result.add(id);}}return List.copyOf(result);}catch(Exception e){throw new IllegalStateException("미확인 빌드 목록을 읽지 못했습니다");}
 }
 static final class Lease implements AutoCloseable {
  private final FileChannel channel;private final FileLock lock;private final Path marker;private final String runKey;
  Lease(FileChannel channel,FileLock lock,Path marker,String runKey){this.channel=channel;this.lock=lock;this.marker=marker;this.runKey=runKey;}
  void resolved(){try{if(!Files.readString(marker).equals(runKey))throw new IllegalStateException();Files.delete(marker);}catch(Exception e){throw new IllegalStateException("실행 결과는 저장했지만 빌더 잠금 정리 확인이 필요합니다",e);}}
  public void close(){try{lock.release();}catch(Exception ignored){}try{channel.close();}catch(Exception ignored){}}
 }
}
