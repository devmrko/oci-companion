package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface BuildTaskStore {
    record Task(String id,String name,String runner,String application,String function,String pipeline,String source,String commit,String goal,String profiles,String directory,String artifact,String sourceId,String branch) {
        public Task { sourceId=sourceId==null?"":sourceId;branch=branch==null?"":branch; }
        public Task(String id,String name,String runner,String application,String function,String pipeline,String source,String commit,String goal,String profiles,String directory,String artifact){this(id,name,runner,application,function,pipeline,source,commit,goal,profiles,directory,artifact,"","");}
    }
    List<Task> list(Connection c,String region,String compartment);
    void save(Connection c,String region,String compartment,Task task);
    void delete(Connection c,String region,String compartment,String id);
}
