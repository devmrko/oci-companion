package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.OperationStatus;
import com.oracle.bmc.devops.requests.*;
import io.github.devmrko.companion.service.PipelineIamRules;
import java.util.function.LongSupplier;
/** Resource GET can expose new fields before DevOps finishes updating its execution snapshot. */
final class OciDevopsWorkRequests {
 @FunctionalInterface interface Sleeper {void sleep(long millis)throws InterruptedException;}
 static void await(DevopsClient client,String id){await(client,id,System::nanoTime,Thread::sleep,60_000_000_000L);}
 static void await(DevopsClient client,String id,LongSupplier now,Sleeper sleep,long budget){
  if(id==null||id.isBlank())throw new IllegalStateException("OCI 변경 작업 ID가 없어 완료를 확인할 수 없습니다.");
  long deadline=now.getAsLong()+budget;
  while(true){
   var work=client.getWorkRequest(GetWorkRequestRequest.builder().workRequestId(id).build()).getWorkRequest();
   if(work.getStatus()==OperationStatus.Succeeded)return;
   if(work.getStatus()==OperationStatus.Failed||work.getStatus()==OperationStatus.Canceled){
    var errors=client.listWorkRequestErrors(ListWorkRequestErrorsRequest.builder().workRequestId(id).limit(10).build()).getWorkRequestErrorCollection().getItems();
    String message=errors.stream().map(e->e.getCode()+": "+e.getMessage()).collect(java.util.stream.Collectors.joining("\n"));
    throw new IllegalStateException("OCI 변경 작업 "+work.getStatus()+" · "+PipelineIamRules.safe(message));
   }
   if(now.getAsLong()>=deadline)throw new IllegalStateException("OCI 변경 작업 완료 대기 시간 초과 · 배포를 시작하지 않았습니다. 작업 상태를 확인한 뒤 다시 미리보기하세요.");
   try{sleep.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("OCI 변경 작업 대기 중단 · 배포를 시작하지 않았습니다.");}
  }
 }
}
