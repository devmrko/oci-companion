package io.github.devmrko.companion.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.*;

/** File/permission check only: never executes a tool or contacts a cluster. */
@Component
public class OkeToolReadiness {
 public record Tool(String name,String configured,String resolved,boolean available,String setting) {}
 private final String kubectl,oci;
 public OkeToolReadiness(@Value("${companion.kubectl:/usr/local/bin/kubectl}") String kubectl,
   @Value("${companion.oci-cli:oci}") String oci){this.kubectl=kubectl;this.oci=oci;}
 public List<Tool> tools(){return List.of(check("kubectl",kubectl,"COMPANION_KUBECTL",false,System.getenv("PATH")),check("OCI CLI",oci,"COMPANION_OCI_CLI",true,System.getenv("PATH")));}
 static Tool check(String name,String command,String setting,boolean allowPath,String searchPath){
  try{
   var path=Path.of(command);
   if(!command.isBlank()&&path.isAbsolute()&&Files.isRegularFile(path)&&Files.isExecutable(path))return new Tool(name,command,path.toString(),true,setting);
   if(allowPath&&!command.isBlank()&&path.getNameCount()==1&&!command.contains("/")&&searchPath!=null)
    for(String directory:searchPath.split(java.io.File.pathSeparator)){
     if(directory.isBlank()||!Path.of(directory).isAbsolute())continue;
     var candidate=Path.of(directory).resolve(command);
     if(Files.isRegularFile(candidate)&&Files.isExecutable(candidate))return new Tool(name,command,candidate.toString(),true,setting);
    }
  }catch(InvalidPathException|SecurityException ignored){}
  return new Tool(name,command,"",false,setting);
 }
}
