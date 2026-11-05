package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildTaskStore;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
public class JsonBuildTaskStore implements BuildTaskStore {
    private final Path root;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public record Data(int version,String identity,List<Task> tasks) {}
    public JsonBuildTaskStore(@Value("${companion.data-dir}") String directory) { root=Path.of(directory).resolve("build-tasks"); }
    private String identity(Connection c,String r,String comp) { return String.join("\n",Boolean.toString(c.demo()),c.profile(),c.tenancy(),c.user(),r,comp); }
    private Path file(String id) {
        try { return root.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)))+".json"); }
        catch(Exception e) { throw new IllegalStateException("빌드 작업 경로 오류"); }
    }
    private List<Task> read(String id) {
        if(!Files.exists(file(id))) return List.of();
        try {
            var data=mapper.readValue(Files.readAllBytes(file(id)),Data.class);
            if(data.version()!=1 || !id.equals(data.identity()) || data.tasks()==null) throw new IllegalStateException();
            return data.tasks();
        } catch(Exception e) { throw new IllegalStateException("빌드 작업 읽기 실패"); }
    }
    public synchronized List<Task> list(Connection c,String r,String comp) { return read(identity(c,r,comp)); }
    public synchronized void save(Connection c,String r,String comp,Task task) {
        String id=identity(c,r,comp);
        var list=new ArrayList<>(read(id));list.removeIf(v->v.id().equals(task.id()));list.add(task);write(id,list);
    }
    public synchronized void delete(Connection c,String r,String comp,String taskId) {
        String id=identity(c,r,comp);
        var list=new ArrayList<>(read(id));list.removeIf(v->v.id().equals(taskId));write(id,list);
    }
    private void write(String id,List<Task> tasks) {
        Path tmp=null;
        try {
            Files.createDirectories(root);Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"));
            tmp=Files.createTempFile(root,".task-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(tmp,mapper.writeValueAsBytes(new Data(1,id,tasks)));
            Files.move(tmp,file(id),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception e) { throw new IllegalStateException("빌드 작업 저장 실패"); }
        finally { if(tmp!=null) try { Files.deleteIfExists(tmp); } catch(Exception ignored) {} }
    }
}
