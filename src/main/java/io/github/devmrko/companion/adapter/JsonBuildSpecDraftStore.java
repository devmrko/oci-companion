package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.port.BuildSpecDraftStore;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonBuildSpecDraftStore implements BuildSpecDraftStore {
    private final Path root;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public record Data(int version,String identity,Draft draft) {}
    public JsonBuildSpecDraftStore(@Value("${companion.data-dir}") String directory){root=Path.of(directory).resolve("build-spec-drafts");}
    private Path file(String identity){try{return root.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)))+".json");}catch(Exception e){throw new IllegalStateException("초안 경로 오류");}}
    public synchronized Optional<Draft> read(String identity){
        var path=file(identity);if(!Files.exists(path))return Optional.empty();
        try{var data=mapper.readValue(Files.readAllBytes(path),Data.class);if(data.version()!=1||!identity.equals(data.identity())||data.draft()==null)throw new IllegalStateException();return Optional.of(data.draft());}catch(Exception e){throw new IllegalStateException("초안 읽기 실패");}
    }
    public synchronized void save(String identity,Draft draft){
        Path tmp=null;
        try{Files.createDirectories(root);Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"));tmp=Files.createTempFile(root,".draft-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));Files.write(tmp,mapper.writeValueAsBytes(new Data(1,identity,draft)));Files.move(tmp,file(identity),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new IllegalStateException("초안 저장 실패");}finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}}
    }
    public synchronized void delete(String identity){try{Files.deleteIfExists(file(identity));}catch(Exception e){throw new IllegalStateException("초안 삭제 실패");}}
}
