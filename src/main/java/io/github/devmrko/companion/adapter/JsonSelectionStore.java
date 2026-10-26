package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.SelectionStore;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonSelectionStore implements SelectionStore {
    private final Path root;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public JsonSelectionStore(@Value("${companion.data-dir}") String directory) { root=Path.of(directory).resolve("settings"); }
    private String identity(Connection c) { return String.join("\n",c.demo()?"demo":"oci",c.profile(),c.tenancy(),c.user()); }
    private Path file(Connection c) {
        try { return root.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity(c).getBytes(StandardCharsets.UTF_8)))+".json"); }
        catch(Exception e) { throw new IllegalStateException("설정 경로 오류"); }
    }
    public synchronized Optional<Saved> read(Connection c) {
        if(!Files.exists(file(c))) return Optional.empty();
        try {
            var saved=mapper.readValue(Files.readAllBytes(file(c)),Saved.class);
            if(saved.version()!=1 || !identity(c).equals(saved.identity()) || saved.region()==null || saved.compartment()==null) throw new IllegalStateException();
            return Optional.of(saved);
        } catch(Exception e) { throw new IllegalStateException("선택 설정 읽기 실패 · 리전·구획을 다시 선택하세요."); }
    }
    public synchronized void write(Connection c,String region,String compartment) {
        Path temp=null;
        try {
            Files.createDirectories(root);Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"));
            temp=Files.createTempFile(root,".selection-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(temp,mapper.writeValueAsBytes(new Saved(1,identity(c),region,compartment)));
            Files.move(temp,file(c),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception e) { throw new IllegalStateException("선택 설정 저장 실패 · 기존 선택 유지"); }
        finally { if(temp!=null) try { Files.deleteIfExists(temp); } catch(Exception ignored) {} }
    }
}
