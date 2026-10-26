package io.github.devmrko.companion.port;
import java.util.List;
import io.github.devmrko.companion.domain.Models.Connection;
public interface CloudConnection {
    List<String> profiles();
    Connection connect(String profile);
}
