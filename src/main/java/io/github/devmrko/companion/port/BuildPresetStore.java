package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.*;
public interface BuildPresetStore {
    record Preset(String name,List<BuildControl.Parameter> parameters,String runName,BuildDefinition.SourceOverride source) {
        public Preset(String name,List<BuildControl.Parameter> parameters) { this(name,parameters,"",BuildDefinition.SourceOverride.none()); }
        public Preset { if(runName==null) runName="";if(source==null) source=BuildDefinition.SourceOverride.none(); }
    }
    List<Preset> list(Connection c,String region,String compartment,String pipeline);
    void save(Connection c,String region,String compartment,String pipeline,Preset preset);
    void delete(Connection c,String region,String compartment,String pipeline,String name);
}
