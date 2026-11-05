package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildInputStore;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
public class JsonBuildInputStore implements BuildInputStore {
    private final Path root;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public record Data(int version,String identity,List<Rule> rules) {}
    public JsonBuildInputStore(@Value("${companion.data-dir}") String directory) { root=Path.of(directory).resolve("build-inputs"); }
    private String identity(Connection c,String r,String comp,String p) { return String.join("\n",Boolean.toString(c.demo()),c.profile(),c.tenancy(),c.user(),r,comp,p); }
    private Path file(String id) {
        try { return root.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)))+".json"); }
        catch(Exception e) { throw new IllegalStateException("입력 규칙 경로 오류"); }
    }
    private List<Rule> read(String id) {
        if(!Files.exists(file(id))) return List.of();
        try {
            var data=mapper.readValue(Files.readAllBytes(file(id)),Data.class);
            if(data.version()!=1 || !id.equals(data.identity()) || data.rules()==null) throw new IllegalStateException();
            return data.rules();
        } catch(Exception e) { throw new IllegalStateException("입력 규칙 읽기 실패"); }
    }
    public synchronized List<Rule> read(Connection c,String r,String comp,String p) { return read(identity(c,r,comp,p)); }
    public synchronized void write(Connection c,String r,String comp,String p,List<Rule> rules) { write(identity(c,r,comp,p),rules); }
    private void write(String id,List<Rule> rules) {
        Path tmp=null;
        try {
            Files.createDirectories(root);Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"));
            tmp=Files.createTempFile(root,".preset-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(tmp,mapper.writeValueAsBytes(new Data(1,id,rules)));
            Files.move(tmp,file(id),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception e) { throw new IllegalStateException("입력 규칙 저장 실패"); }
        finally { if(tmp!=null) try { Files.deleteIfExists(tmp); } catch(Exception ignored) {} }
    }
}
