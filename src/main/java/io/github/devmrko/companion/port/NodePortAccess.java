package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface NodePortAccess {
 record SecurityList(String id,String name){}
 record Rule(String source,String protocol,int fromPort,int toPort,String description){}
 default List<Rule> references(Connection c,String region,String compartment,String cluster,String list,int port){throw io.github.devmrko.companion.service.Errors.SCOPE.exception();}
 record Preview(String cluster,String list,String name,String source,int port,boolean remove,String etag,int existingRules,String marker){}
 List<SecurityList> lists(Connection c,String region,String compartment,String cluster);
 Preview preview(Connection c,String region,String compartment,String cluster,String list,String source,int port,boolean remove);
 void execute(Connection c,String region,String compartment,Preview preview,String token);
}
