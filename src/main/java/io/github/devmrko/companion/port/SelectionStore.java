package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.Optional;

public interface SelectionStore {
    record Saved(int version,String identity,String region,String compartment) {}
    Optional<Saved> read(Connection connection);
    void write(Connection connection,String region,String compartment);
}
