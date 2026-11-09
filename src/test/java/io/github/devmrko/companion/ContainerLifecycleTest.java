package io.github.devmrko.companion.adapter;

import com.oracle.bmc.containerinstances.ContainerInstanceClient;
import com.oracle.bmc.containerinstances.model.ContainerInstance;
import com.oracle.bmc.containerinstances.responses.GetContainerInstanceResponse;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ContainerPreparation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContainerLifecycleTest {
 final Connection c=new Connection("DEFAULT","tenant","user","home","label",false);
 @Test void allowedActionsFollowNativeLifecycle(){
  assertTrue(Action.START.allowed("INACTIVE"));assertFalse(Action.START.allowed("FAILED"));
  assertTrue(Action.STOP.allowed("ACTIVE"));assertTrue(Action.RESTART.allowed("ACTIVE"));assertTrue(Action.DELETE.allowed("FAILED"));
  for(var a:Action.values()){assertFalse(a.allowed("CREATING"));assertFalse(a.allowed("UPDATING"));assertFalse(a.allowed("DELETED"));}
 }
 @Test void nativeActionUsesExactEtagAndRejectsChangedResource() throws Exception {
  var adapter=spy(new OciContainerPreparation(mock(OciBuildHistory.class)));var client=mock(ContainerInstanceClient.class);doReturn(client).when(adapter).client(c,"r");
  when(client.getContainerInstance(any())).thenReturn(GetContainerInstanceResponse.builder().etag("v1").containerInstance(ContainerInstance.builder().id("id").displayName("hello").compartmentId("comp").lifecycleState(ContainerInstance.LifecycleState.Active).build()).build());
  var expected=adapter.planAction(c,"r","comp","id",Action.STOP);adapter.applyAction(c,"r","comp",expected);
  verify(client).stopContainerInstance(argThat(r->r.getIfMatch().equals("v1")&&r.getContainerInstanceId().equals("id")));
  assertThrows(RuntimeException.class,()->adapter.planAction(c,"r","other","id",Action.DELETE));
  assertThrows(RuntimeException.class,()->adapter.planAction(c,"r","comp","id",Action.START));
  assertThrows(RuntimeException.class,()->adapter.applyAction(c,"r","comp",new Operation("id","hello","ACTIVE","old",Action.DELETE)));
  verify(client,never()).deleteContainerInstance(any());verify(client,never()).startContainerInstance(any());
 }
 @Test void allNativeOperationsCarryConditionalWrite() throws Exception {
  for(var action:Action.values()){
   var adapter=spy(new OciContainerPreparation(mock(OciBuildHistory.class)));var client=mock(ContainerInstanceClient.class);doReturn(client).when(adapter).client(c,"r");var op=new Operation("id","hello",action==Action.START?"INACTIVE":"ACTIVE","etag",action);doReturn(op).when(adapter).planAction(c,"r","comp","id",action);adapter.applyAction(c,"r","comp",op);
   switch(action){case START -> verify(client).startContainerInstance(argThat(r->"etag".equals(r.getIfMatch())));case STOP -> verify(client).stopContainerInstance(argThat(r->"etag".equals(r.getIfMatch())));case RESTART -> verify(client).restartContainerInstance(argThat(r->"etag".equals(r.getIfMatch())));case DELETE -> verify(client).deleteContainerInstance(argThat(r->"etag".equals(r.getIfMatch())));}
  }
 }
}
