package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface BuildControl {
    record Parameter(String name,String value,String description) {}
    record Settings(String etag,List<Parameter> parameters) {}
    Settings settings(Connection c,String region,String compartment,String pipeline);
    void defaults(Connection c,String region,String compartment,String pipeline,String etag,List<Parameter> parameters);
    String start(Connection c,String region,String compartment,String pipeline,String etag,String name,List<Parameter> parameters,String token);
}
