package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface BuildTaskStore {
    record Task(String id,String name,String runner,String application,String function,String pipeline,String source,String commit,String goal,String profiles,String directory,String artifact,String sourceId,String branch,String target,String architecture) {
        public Task { target=target==null?"UNSPECIFIED":target;architecture=architecture==null?"UNKNOWN":architecture;sourceId=sourceId==null?"":sourceId;branch=branch==null?"":branch; }
        public Task(String id,String name,String runner,String application,String function,String pipeline,String source,String commit,String goal,String profiles,String directory,String artifact,String sourceId,String branch){this(id,name,runner,application,function,pipeline,source,commit,goal,profiles,directory,artifact,sourceId,branch,"UNSPECIFIED","UNKNOWN");}
        public String targetLabel(){return switch(target){case "VM" -> "VM용 빌드";case "CONTAINER" -> "컨테이너 빌드 (OKE · OCI Container)";case "FUNCTION" -> "Function 빌드";default -> "유형 미지정";};}
        public String architectureLabel(){return switch(architecture){case "X86_64" -> "x86 (amd64)";case "ARM64" -> "ARM (arm64)";default -> "아키텍처 미지정";};}
        public Task(String id,String name,String runner,String application,String function,String pipeline,String source,String commit,String goal,String profiles,String directory,String artifact){this(id,name,runner,application,function,pipeline,source,commit,goal,profiles,directory,artifact,"","");}
    }
    List<Task> list(Connection c,String region,String compartment);
    void save(Connection c,String region,String compartment,Task task);
    void delete(Connection c,String region,String compartment,String id);
}
