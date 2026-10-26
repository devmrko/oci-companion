package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

public interface BuildHistory {
    record Pipeline(String id,String name,String state,String projectId,String projectName) {
        public Pipeline(String id,String name,String state) { this(id,name,state,"",""); }
    }
    record Run(String id,String name,String state,String createdAt) {}
    record Step(String name,String state) {}
    record Detail(String id,String name,String state,String message,List<Step> steps) {}
    List<Pipeline> pipelines(Connection connection,String region,String compartment);
    List<Run> runs(Connection connection,String region,String compartment,String pipeline);
    Detail detail(Connection connection,String region,String compartment,String pipeline,String run);
}
