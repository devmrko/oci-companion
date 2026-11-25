package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.NodePortAccess;
import io.github.devmrko.companion.service.Errors;
import java.util.List;
@org.springframework.stereotype.Component @org.springframework.context.annotation.Profile("demo")
public class DemoNodePortAccess implements NodePortAccess {
 public List<SecurityList> lists(Connection c,String region,String compartment,String cluster){return List.of();}
 public Preview preview(Connection c,String region,String compartment,String cluster,String list,String source,int port,boolean remove){throw Errors.INVALID.exception();}
 public void execute(Connection c,String region,String compartment,Preview preview,String token){throw Errors.INVALID.exception();}
}
