package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.*;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.Errors;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component @Profile("demo")
public class DemoCloud implements CloudConnection, PipelineGateway {
    public List<String> profiles() { return List.of("DEMO"); }
    public Connection connect(String profile) {
        if (!profile.equals("DEMO")) throw Errors.CONFIG.exception();
        return new Connection("DEMO","demo-tenancy","demo-user","ap-seoul-1","데모 작업 공간",true);
    }
    public List<Pipeline> pipelines(Connection c,String compartmentId) {
        return List.of(new Pipeline("demo-build","DB Companion · Maven build","BUILD"),new Pipeline("demo-vm","DB Companion · VM deploy","DEPLOY"));
    }
    public void validate(Connection c,Workspace w,String kind) {
        if (!("BUILD".equals(kind)?w.buildPipelineId().equals("demo-build"):w.deployPipelineId().equals("demo-vm"))) throw Errors.INVALID.exception();
    }
    public RemoteRun start(Connection c,String kind,String id,String token) { return new RemoteRun("demo-"+token,"DEMO_ACCEPTED"); }
    public RemoteRun status(Connection c,String kind,String id) { return new RemoteRun(id,"DEMO_SUCCEEDED"); }
}
