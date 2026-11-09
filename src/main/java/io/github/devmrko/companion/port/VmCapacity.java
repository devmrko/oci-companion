package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface VmCapacity {
    record Disk(String kind,String name,String sizeGb,String state) {}
    record Capacity(String name,String state,String shape,String ocpus,String vcpus,String memoryGb,String localDiskGb,List<Disk> disks,List<String> notices) {}
    Capacity read(Connection connection,String region,String compartment,String instance);
}
