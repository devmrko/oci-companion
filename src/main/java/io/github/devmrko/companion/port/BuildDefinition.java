package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;

public interface BuildDefinition {
    record Source(String key,String name,String provider,String repositoryId,String url,String branch,boolean primary) {}
    record Stage(String id,String name,String type,String state,List<String> after,String image,String spec,int timeout,
                 List<Source> sources,List<String> artifacts) {}
    record Definition(String name,String project,String state,String description,String revision,List<Stage> stages) {
        public List<Source> sources() { return stages.stream().flatMap(s->s.sources().stream()).toList(); }
        public boolean runnable() { return "ACTIVE".equals(state) && !stages.isEmpty() && stages.stream().allMatch(s->"ACTIVE".equals(s.state())); }
    }
    record Choice(String value,String label) {}
    record SourceOverride(String source,String branch,String commit) {
        public static SourceOverride none() { return new SourceOverride("","",""); }
    }
    record Specification(String content,String note,List<String> outputs,List<String> scripts,List<String> sourceCommands) {
        public Specification(String content,String note,List<String> outputs,List<String> scripts) { this(content,note,outputs,scripts,List.of()); }
    }
    Definition read(Connection c,String region,String compartment,String pipeline);
    List<Choice> branches(Connection c,String region,Source source);
    List<Choice> commits(Connection c,String region,Source source,String branch);
    default boolean validCommit(Connection c,String region,Source source,String commit) { return false; }
    Specification specification(Connection c,String region,Source source,String path);
}
