package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

public interface BuildHistory {
    record Pipeline(String id,String name,String state,String projectId,String projectName) {
        public Pipeline(String id,String name,String state) { this(id,name,state,"",""); }
    }
    record Run(String id,String name,String state,String createdAt) {}
    record Step(String name,String state,String type) { public Step(String name,String state) { this(name,state,""); } }
    record StoredArtifact(String name,String kind,String location,String version,String id) {}
    record Detail(String id,String name,String state,String message,List<Step> steps,List<StoredArtifact> artifacts) {
        public Detail(String id,String name,String state,String message,List<Step> steps) { this(id,name,state,message,steps,List.of()); }
        public String buildState() { return phase("BUILD"); }
        public String storageState() { return phase("DELIVER"); }
        private String phase(String type) {
            var states=steps.stream().filter(s->type.equals(s.type())).map(Step::state).toList();
            if(states.isEmpty()) return "기록 없음";
            if(states.stream().allMatch(s->s.equals("SUCCEEDED"))) return "SUCCEEDED";
            return states.stream().filter(s->s.equals("FAILED") || s.equals("CANCELED")).findFirst()
                .orElse(states.stream().filter(s->!s.equals("SUCCEEDED")).findFirst().orElse("UNKNOWN"));
        }
    }
    List<Pipeline> pipelines(Connection connection,String region,String compartment);
    List<Run> runs(Connection connection,String region,String compartment,String pipeline);
    io.github.devmrko.companion.service.TestReports.Summary reports(Connection connection,String region,String compartment,String pipeline,String run,String artifact);
    Detail detail(Connection connection,String region,String compartment,String pipeline,String run);
}
