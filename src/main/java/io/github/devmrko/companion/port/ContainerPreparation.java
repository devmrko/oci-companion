package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
import java.util.Map;
public interface ContainerPreparation {
 record Instance(String id,String name,String state,String shape,String cpu,String memory) {}
 record Process(String id,String name,String state,String detail,String exitCode,String image) {}
 record Work(String id,String state,String percent,String error) {}
 record Detail(String id,String name,String state,String detail,List<Process> processes,List<Work> work) {
  public Detail(String id,String name,String state,String detail,List<Process> processes){this(id,name,state,detail,processes,List.of());}
 }
 record Logs(String text,boolean truncated) {}
 Detail read(Connection c,String region,String compartment,String instance);
 Logs logs(Connection c,String region,String compartment,String instance,String container);
 record Shape(String name,String processor,VmSizing.Limits limits) {}
 record Subnet(String id,String name,String vcn,boolean privateOnly) {}
 record Options(List<String> domains,List<Shape> shapes,List<Subnet> subnets) {}
 record Input(String name,String domain,String shape,float cpu,float memory,String subnet,boolean publicIp,String image,Map<String,String> variables,List<String> command,List<String> arguments,int healthPort,String healthPath) {
  public Input(String name,String domain,String shape,float cpu,float memory,String subnet,boolean publicIp,String image,Map<String,String> variables,List<String> command,List<String> arguments){this(name,domain,shape,cpu,memory,subnet,publicIp,image,variables,command,arguments,0,"");}
 }
 record Preview(Input input,String imageUrl,String json,String fingerprint) {}
 List<Instance> list(Connection c,String region,String compartment);
 Options options(Connection c,String region,String compartment,String domain);
 Preview preview(Connection c,String region,String compartment,Input input);
 String create(Connection c,String region,String compartment,Preview preview,String token);
}
