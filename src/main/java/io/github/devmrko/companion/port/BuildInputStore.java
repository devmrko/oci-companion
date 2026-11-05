package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface BuildInputStore {
    record Rule(String name,String type,String requirement,List<String> choices) {}
    List<Rule> read(Connection c,String region,String compartment,String pipeline);
    void write(Connection c,String region,String compartment,String pipeline,List<Rule> rules);
}
