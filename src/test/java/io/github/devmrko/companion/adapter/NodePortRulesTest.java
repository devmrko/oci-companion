package io.github.devmrko.companion.adapter;
import com.oracle.bmc.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NodePortRulesTest {
 @Test void addAndRemovePreserveUnrelatedRules(){
  var existing=IngressSecurityRule.builder().source("10.0.0.0/24").protocol("all").description("existing rule").build();
  var added=OciNodePortAccess.rules(List.of(existing),"192.0.2.10/32",31084,false);assertEquals(2,added.size());assertSame(existing,added.getFirst());assertEquals("6",added.get(1).getProtocol());assertEquals(31084,added.get(1).getTcpOptions().getDestinationPortRange().getMin());
  assertEquals(List.of(existing),OciNodePortAccess.rules(added,"192.0.2.10/32",31084,true));
  assertThrows(RuntimeException.class,()->OciNodePortAccess.rules(List.of(existing),"192.0.2.10/32",31084,true));
  assertThrows(RuntimeException.class,()->OciNodePortAccess.rules(added,"192.0.2.10/32",31084,false));
 }
 @Test void refusesBroaderSourcesAndInvalidPorts(){for(String source:List.of("0.0.0.0/0","192.0.2.0/24","300.0.0.1/32","192.000.2.1/32","hostname/32"))assertThrows(RuntimeException.class,()->OciNodePortAccess.validate(source,31084));assertThrows(RuntimeException.class,()->OciNodePortAccess.validate("192.0.2.10/32",443));}
 @Test void doesNotRemoveUnmarkedExistingExactRule(){var rule=IngressSecurityRule.builder().source("192.0.2.10/32").sourceType(IngressSecurityRule.SourceType.CidrBlock).protocol("6").isStateless(false).description("operator rule").tcpOptions(TcpOptions.builder().destinationPortRange(PortRange.builder().min(31084).max(31084).build()).build()).build();assertThrows(RuntimeException.class,()->OciNodePortAccess.rules(List.of(rule),"192.0.2.10/32",31084,true));}
}
