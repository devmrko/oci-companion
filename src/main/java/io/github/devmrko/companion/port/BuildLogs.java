package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface BuildLogs {
    record Line(String time,String stage,String message) {}
    record Page(String state,String note,List<Line> lines,String next,String until) {}
    Page read(Connection c,String region,String compartment,String pipeline,String run,String page,String until);
}
