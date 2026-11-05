package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.*;
import java.util.List;
public interface ResourceCatalog {
    List<Choice> regions(Connection connection);
    List<Choice> compartments(Connection connection);
    List<Resource> resources(Connection connection,String region,String compartment);
}
