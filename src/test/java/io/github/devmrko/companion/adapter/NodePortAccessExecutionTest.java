package io.github.devmrko.companion.adapter;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.responses.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NodePortAccessExecutionTest {
 @Test void backupPrecedesConditionalUpdateAndBackupFailurePreventsMutation()throws Exception {
  var c=new Connection("p","t","u","r","label",false);var clients=mock(OciOkePreparation.class);var network=mock(VirtualNetworkClient.class);var backups=mock(BuildSpecDraftStore.class);when(clients.network(c,"r")).thenReturn(network);
  var adapter=spy(new OciNodePortAccess(clients,backups));var p=new NodePortAccess.Preview("cluster","list","workers","192.0.2.10/32",31084,false,"v1",0,OciNodePortAccess.marker("192.0.2.10/32",31084));
  doReturn(p).when(adapter).preview(c,"r","comp","cluster","list","192.0.2.10/32",31084,false);
  when(network.getSecurityList(any())).thenReturn(GetSecurityListResponse.builder().etag("v1").securityList(SecurityList.builder().id("list").ingressSecurityRules(List.of()).egressSecurityRules(List.of(EgressSecurityRule.builder().destination("0.0.0.0/0").protocol("all").build())).build()).build());
  adapter.execute(c,"r","comp",p,"ticket");var order=inOrder(backups,network);order.verify(network).getSecurityList(any());order.verify(backups).save(any(),any());order.verify(network).updateSecurityList(argThat(x->"v1".equals(x.getIfMatch())&&x.getUpdateSecurityListDetails().getEgressSecurityRules()==null&&x.getUpdateSecurityListDetails().getIngressSecurityRules().size()==1));
  clearInvocations(network,backups);doThrow(new IllegalStateException("storage failure")).when(backups).save(any(),any());assertThrows(RuntimeException.class,()->adapter.execute(c,"r","comp",p,"ticket2"));verify(network,never()).updateSecurityList(any());
 }
}
