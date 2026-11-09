package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Errors;
import java.util.List;

public interface VmSizing {
    record Limits(float minCpu,float maxCpu,float minMemory,float maxMemory,float minPerCpu,float maxPerCpu) {
        public void validate(float cpu,float memory) {
            if(!Float.isFinite(cpu)||!Float.isFinite(memory)||cpu<minCpu||cpu>maxCpu||memory<minMemory||memory>maxMemory||memory<cpu*minPerCpu||memory>cpu*maxPerCpu)throw Errors.CAPACITY_INPUT.exception();
        }
    }
    record Volume(String id,String kind,String name,long size,String etag) {}
    record Snapshot(String instance,String name,String state,String shape,float cpu,float memory,String etag,Limits limits,List<Volume> volumes) {}
    record Change(String volume,float cpu,float memory,long size) {}
    Snapshot read(Connection c,String region,String compartment,String instance);
    void apply(Connection c,String region,String compartment,Snapshot before,Change change);
    static void validate(Snapshot before,Change change) {
        if(change.volume().isBlank()) {
            if(!"STOPPED".equals(before.state())||before.limits()==null)throw Errors.CAPACITY_STATE.exception();
            before.limits().validate(change.cpu(),change.memory());
            if(change.cpu()==before.cpu()&&change.memory()==before.memory())throw Errors.CAPACITY_INPUT.exception();
        } else {
            var v=before.volumes().stream().filter(x->x.id().equals(change.volume())).findFirst().orElseThrow(Errors.SCOPE::exception);
            if(change.size()<=v.size()||change.size()>32768)throw Errors.CAPACITY_INPUT.exception();
        }
    }
}
