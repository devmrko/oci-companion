package io.github.devmrko.companion;
import io.github.devmrko.companion.port.VmSizing;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class VmSizingTest {
 final VmSizing.Limits limits=new VmSizing.Limits(1,8,1,128,1,16);
 VmSizing.Snapshot snapshot(String state){return new VmSizing.Snapshot("vm","test",state,"Flex",1,8,"etag",limits,List.of(new VmSizing.Volume("boot","BOOT","boot",50,"vtag")));}
 @Test void rejectsNonFiniteAndMemoryRatio(){for(float cpu:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,9})assertThrows(RuntimeException.class,()->limits.validate(cpu,8));assertThrows(RuntimeException.class,()->limits.validate(2,40));assertThrows(RuntimeException.class,()->limits.validate(4,2));assertThrows(RuntimeException.class,()->limits.validate(2,Float.NaN));limits.validate(2,16);}
 @Test void cannotResizeRunningVmOrRepeatCurrentValues(){assertThrows(RuntimeException.class,()->VmSizing.validate(snapshot("RUNNING"),new VmSizing.Change("",2,16,0)));assertThrows(RuntimeException.class,()->VmSizing.validate(snapshot("STOPPED"),new VmSizing.Change("",1,8,0)));VmSizing.validate(snapshot("STOPPED"),new VmSizing.Change("",2,16,0));}
 @Test void volumesMustBeAttachedAndOnlyGrow(){for(long size:new long[]{49,50,32769})assertThrows(RuntimeException.class,()->VmSizing.validate(snapshot("STOPPED"),new VmSizing.Change("boot",0,0,size)));assertThrows(RuntimeException.class,()->VmSizing.validate(snapshot("STOPPED"),new VmSizing.Change("other",0,0,100)));VmSizing.validate(snapshot("STOPPED"),new VmSizing.Change("boot",0,0,51));}
}
