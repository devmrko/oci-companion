package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface OkePodLogs {
 record Pod(String name,String uid,List<String> containers){}
 record Choices(String namespace,String deployment,List<Pod> pods){}
 record Result(String state,String text,String guidance,String checkedAt){}
 Choices choices(Connection c,String region,String compartment,String app);
 Result read(Connection c,String region,String compartment,String app,String pod,String uid,String container,boolean previous,int lines);
}
