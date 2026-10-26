package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Catalog.*;
import io.github.devmrko.companion.port.CatalogStore;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
public class JsonCatalogStore implements CatalogStore {
    private final Path root;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public JsonCatalogStore(@Value("${companion.data-dir}") String directory) { root=Path.of(directory); }
    private Path path(String scope,String suffix) {
        try { return root.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)))+suffix+".json"); }
        catch (Exception e) { throw new IllegalStateException("저장 경로 오류"); }
    }
    public synchronized Optional<Snapshot> read(String scope) {
        Path file=path(scope,"-cache");
        if (!Files.exists(file)) return Optional.empty();
        try {
            var snapshot=mapper.readValue(Files.readAllBytes(file),Snapshot.class);
            if (snapshot.version()!=1 || !scope.equals(snapshot.scope()) || snapshot.resources()==null) throw new IllegalStateException();
            return Optional.of(snapshot);
        } catch (Exception e) { throw new IllegalStateException("캐시 읽기 실패 · 새로 고침 필요"); }
    }
    public synchronized void write(Snapshot snapshot) { persist(path(snapshot.scope(),"-cache"),snapshot); }
    public synchronized Sources sources(String scope) {
        Path file=path(scope,"-sources");
        if (!Files.exists(file)) return new Sources(1,scope,List.of());
        try {
            var sources=mapper.readValue(Files.readAllBytes(file),Sources.class);
            if (sources.version()!=1 || !scope.equals(sources.scope()) || sources.items()==null) throw new IllegalStateException();
            return sources;
        } catch (Exception e) { throw new IllegalStateException("소스 파일 읽기 실패 · 원본 파일 확인 필요"); }
    }
    public synchronized void saveSource(String scope,Source source) {
        var items=new ArrayList<>(sources(scope).items());
        items.removeIf(s -> s.provider().equals(source.provider()) && s.url().equals(source.url()));
        items.add(source); persist(path(scope,"-sources"),new Sources(1,scope,List.copyOf(items)));
    }
    public synchronized void deleteSource(String scope,String id) {
        var items=new ArrayList<>(sources(scope).items()); items.removeIf(s -> s.id().equals(id));
        persist(path(scope,"-sources"),new Sources(1,scope,List.copyOf(items)));
    }
    public synchronized void migrateExternalSources(String identityPrefix,String destination) {
        if (!Files.exists(root)) return;
        try (var paths=Files.list(root)) {
            for (var file:paths.filter(p -> p.getFileName().toString().endsWith("-sources.json")).sorted().toList()) {
                Sources legacy;
                try { legacy=mapper.readValue(Files.readAllBytes(file),Sources.class); }
                catch (Exception e) { continue; } // Unreadable files remain untouched.
                if (legacy.scope()==null || !legacy.scope().startsWith(identityPrefix) || legacy.scope().equals(destination)
                    || legacy.version()!=1 || legacy.items()==null || !file.equals(path(legacy.scope(),"-sources"))) continue;
                var external=legacy.items().stream().filter(s -> Set.of("GITHUB","GITLAB").contains(s.provider())).toList();
                if (external.isEmpty()) continue;
                var merged=new ArrayList<>(sources(destination).items());
                for (var source:external) if (merged.stream().noneMatch(s -> s.id().equals(source.id()) || (s.provider().equals(source.provider()) && s.url().equals(source.url()) && s.branch().equals(source.branch()) && s.name().equals(source.name())))) merged.add(source);
                persist(path(destination,"-sources"),new Sources(1,destination,List.copyOf(merged)));
                // Destination is durable before removing old entries. Repeating after interruption is safe.
                persist(file,new Sources(1,legacy.scope(),legacy.items().stream().filter(s -> !Set.of("GITHUB","GITLAB").contains(s.provider())).toList()));
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("소스 이전 실패 · 기존 파일 보존"); }
    }
    private void persist(Path destination,Object value) {
        Path temporary=null;
        try {
            Files.createDirectories(root);
            Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"));
            temporary=Files.createTempFile(root,".write-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(temporary,mapper.writeValueAsBytes(value));
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { throw new IllegalStateException("파일 저장 실패"); }
        finally { if (temporary!=null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {} }
    }
}
