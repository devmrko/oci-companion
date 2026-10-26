package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
public interface BuildPresetStore {
    record Preset(String name,List<BuildControl.Parameter> parameters) {}
    List<Preset> list(Connection c,String region,String compartment,String pipeline);
    void save(Connection c,String region,String compartment,String pipeline,Preset preset);
    void delete(Connection c,String region,String compartment,String pipeline,String name);
}
