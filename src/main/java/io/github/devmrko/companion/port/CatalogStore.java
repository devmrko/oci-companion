package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Catalog.*;
import java.util.Optional;
public interface CatalogStore {
    Optional<Snapshot> read(String scope);
    void write(Snapshot snapshot);
    Sources sources(String scope);
    void saveSource(String scope,Source source);
    void deleteSource(String scope,String id);
}
