package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.core.*;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.VmCapacity;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciVmCapacity implements VmCapacity {
    private final OciBuildHistory clients;
    public OciVmCapacity(OciBuildHistory clients){this.clients=clients;}
    ComputeClient compute(Connection c,String region)throws Exception{var x=ComputeClient.builder().configuration(configuration()).build(clients.authentication(c));x.setRegion(region);return x;}
    BlockstorageClient storage(Connection c,String region)throws Exception{var x=BlockstorageClient.builder().configuration(configuration()).build(clients.authentication(c));x.setRegion(region);return x;}
    private ClientConfiguration configuration(){return ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(10000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();}
    private String number(Object value){return Objects.toString(value,"미확인");}
    public Capacity read(Connection c,String region,String comp,String id){
        try(var compute=compute(c,region)){
            var vm=compute.getInstance(GetInstanceRequest.builder().instanceId(id).build()).getInstance();if(!comp.equals(vm.getCompartmentId()))throw Errors.SCOPE.exception();
            var disks=new ArrayList<Disk>();var notices=new ArrayList<String>();var shape=vm.getShapeConfig();
            try(var storage=storage(c,region)){
                try{for(var a:compute.getPaginators().listBootVolumeAttachmentsRecordIterator(ListBootVolumeAttachmentsRequest.builder().compartmentId(comp).availabilityDomain(vm.getAvailabilityDomain()).instanceId(id).build())){
                    if(!"ATTACHED".equals(a.getLifecycleState().getValue()))continue;
                    try{var v=storage.getBootVolume(GetBootVolumeRequest.builder().bootVolumeId(a.getBootVolumeId()).build()).getBootVolume();if(!comp.equals(v.getCompartmentId())){notices.add("다른 구획의 부트 볼륨 · 해당 구획에서 확인");continue;}disks.add(new Disk("부트",v.getDisplayName(),number(v.getSizeInGBs()),v.getLifecycleState().getValue()));}
                    catch(Exception e){notices.add("부트 볼륨 용량 조회 실패 · Block Volume 읽기 권한 확인");}
                }}catch(Exception e){notices.add("부트 볼륨 연결 조회 실패");}
                try{for(var a:compute.getPaginators().listVolumeAttachmentsRecordIterator(ListVolumeAttachmentsRequest.builder().compartmentId(comp).instanceId(id).build())){
                    if(!"ATTACHED".equals(a.getLifecycleState().getValue()))continue;
                    try{var v=storage.getVolume(GetVolumeRequest.builder().volumeId(a.getVolumeId()).build()).getVolume();if(!comp.equals(v.getCompartmentId())){notices.add("다른 구획의 블록 볼륨 · 해당 구획에서 확인");continue;}disks.add(new Disk("블록",v.getDisplayName(),number(v.getSizeInGBs()),v.getLifecycleState().getValue()));}
                    catch(Exception e){notices.add("블록 볼륨 용량 조회 실패 · Block Volume 읽기 권한 확인");}
                }}catch(Exception e){notices.add("블록 볼륨 연결 조회 실패");}
            }catch(Exception e){notices.add("스토리지 조회 실패 · 연결·권한 확인");}
            return new Capacity(vm.getDisplayName(),vm.getLifecycleState().getValue(),vm.getShape(),shape==null?"미확인":number(shape.getOcpus()),shape==null?"미확인":number(shape.getVcpus()),shape==null?"미확인":number(shape.getMemoryInGBs()),shape==null?"미확인":number(shape.getLocalDisksTotalSizeInGBs()),List.copyOf(disks),List.copyOf(notices));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
}
