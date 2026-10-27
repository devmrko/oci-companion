package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface GitCredentials {
    String secret(Connection connection,String region,String host,String reference);
    boolean allowedHost(String host);
    record Authentication(String username,String password) { public String toString(){return "Git authentication [redacted]";} }
    Authentication configured(String host);
}
