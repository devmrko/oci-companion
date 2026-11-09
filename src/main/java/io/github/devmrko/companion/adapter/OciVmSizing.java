package io.github.devmrko.companion.adapter;

import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.*;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.retrier.RetryConfiguration;
import com.oracle.bmc.core.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.VmSizing;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OciVmSizing implements VmSizing {
    private final OciBuildHistory clients;
    public OciVmSizing(OciBuildHistory clients){this.clients=clients;}
    private ClientConfiguration configuration(){return ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(10000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();}
    ComputeClient compute(Connection c,String region)throws Exception{var x=ComputeClient.builder().configuration(configuration()).build(clients.authentication(c));x.setRegion(region);return x;}
    BlockstorageClient storage(Connection c,String region)throws Exception{var x=BlockstorageClient.builder().configuration(configuration()).build(clients.authentication(c));x.setRegion(region);return x;}
    public Snapshot read(Connection c,String region,String comp,String id) {
        try(var compute=compute(c,region);var storage=storage(c,region)) {
            var response=compute.getInstance(GetInstanceRequest.builder().instanceId(id).build());var vm=response.getInstance();
            if(!comp.equals(vm.getCompartmentId()))throw Errors.SCOPE.exception();
            Limits limits=null;
            for(var s:compute.getPaginators().listShapesRecordIterator(ListShapesRequest.builder().compartmentId(comp).availabilityDomain(vm.getAvailabilityDomain()).imageId(vm.getImageId()).build())) {
                if(!vm.getShape().equals(s.getShape())||!Boolean.TRUE.equals(s.getIsFlexible())||!vm.getShape().startsWith("VM.")||vm.getShape().contains("DenseIO"))continue;
                var cpu=s.getOcpuOptions();var mem=s.getMemoryOptions();
                if(cpu!=null&&mem!=null&&cpu.getMin()!=null&&cpu.getMax()!=null&&mem.getMinInGBs()!=null&&mem.getMaxInGBs()!=null&&mem.getMinPerOcpuInGBs()!=null&&mem.getMaxPerOcpuInGBs()!=null)
                    limits=new Limits(cpu.getMin(),cpu.getMax(),mem.getMinInGBs(),mem.getMaxInGBs(),mem.getMinPerOcpuInGBs(),mem.getMaxPerOcpuInGBs());
            }
            var volumes=new ArrayList<Volume>();
            for(var a:compute.getPaginators().listBootVolumeAttachmentsRecordIterator(ListBootVolumeAttachmentsRequest.builder().compartmentId(comp).availabilityDomain(vm.getAvailabilityDomain()).instanceId(id).build())) {
                if(!"ATTACHED".equals(a.getLifecycleState().getValue()))continue;
                var r=storage.getBootVolume(GetBootVolumeRequest.builder().bootVolumeId(a.getBootVolumeId()).build());var v=r.getBootVolume();
                if(comp.equals(v.getCompartmentId())&&"AVAILABLE".equals(v.getLifecycleState().getValue()))volumes.add(new Volume(v.getId(),"BOOT",v.getDisplayName(),v.getSizeInGBs(),r.getEtag()));
            }
            for(var a:compute.getPaginators().listVolumeAttachmentsRecordIterator(ListVolumeAttachmentsRequest.builder().compartmentId(comp).instanceId(id).build())) {
                if(!"ATTACHED".equals(a.getLifecycleState().getValue()))continue;
                var r=storage.getVolume(GetVolumeRequest.builder().volumeId(a.getVolumeId()).build());var v=r.getVolume();
                if(comp.equals(v.getCompartmentId())&&"AVAILABLE".equals(v.getLifecycleState().getValue()))volumes.add(new Volume(v.getId(),"BLOCK",v.getDisplayName(),v.getSizeInGBs(),r.getEtag()));
            }
            volumes.sort(Comparator.comparing(Volume::id));var config=vm.getShapeConfig();
            return new Snapshot(id,vm.getDisplayName(),vm.getLifecycleState().getValue(),vm.getShape(),config.getOcpus(),config.getMemoryInGBs(),response.getEtag(),limits,List.copyOf(volumes));
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
    }
    public void apply(Connection c,String region,String comp,Snapshot before,Change change) {
        VmSizing.validate(before,change);
        var current=read(c,region,comp,before.instance());
        if(!before.equals(current))throw Errors.BUILD_CHANGED.exception();
        try {
            if(change.volume().isBlank()) {
                if(before.etag()==null||before.etag().isBlank())throw Errors.BUILD_CHANGED.exception();
                try(var compute=compute(c,region)) {
                    compute.updateInstance(UpdateInstanceRequest.builder().instanceId(before.instance()).ifMatch(before.etag()).updateInstanceDetails(UpdateInstanceDetails.builder().shapeConfig(UpdateInstanceShapeConfigDetails.builder().ocpus(change.cpu()).memoryInGBs(change.memory()).build()).build()).build());
                }
            } else {
                var v=before.volumes().stream().filter(x->x.id().equals(change.volume())).findFirst().orElseThrow();
                if(v.etag()==null||v.etag().isBlank())throw Errors.BUILD_CHANGED.exception();
                try(var storage=storage(c,region)) {
                    if(v.kind().equals("BOOT"))storage.updateBootVolume(UpdateBootVolumeRequest.builder().bootVolumeId(v.id()).ifMatch(v.etag()).updateBootVolumeDetails(UpdateBootVolumeDetails.builder().sizeInGBs(change.size()).build()).build());
                    else storage.updateVolume(UpdateVolumeRequest.builder().volumeId(v.id()).ifMatch(v.etag()).updateVolumeDetails(UpdateVolumeDetails.builder().sizeInGBs(change.size()).build()).build());
                }
            }
        }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CAPACITY_UNKNOWN.exception();}
    }
}
