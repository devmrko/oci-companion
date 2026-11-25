package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.service.Errors;
import java.util.*;
import org.yaml.snakeyaml.*;
import tools.jackson.databind.json.JsonMapper;

/** Reuse the live workload spec without introducing a Service or Namespace mutation. */
final class ExistingOkeDeployment {
 static String version(String json){
  try {var version=JsonMapper.builder().build().readTree(json).path("metadata").path("resourceVersion").asText();if(version.isBlank())throw new IllegalArgumentException();return version;}
  catch(Exception e){throw Errors.DEPLOYMENT_RUNTIME.exception();}
 }
 @SuppressWarnings("unchecked")
 static String render(String json,String namespace,String name,String image){
  try {
   if(json==null||json.length()>524288||!image.matches("[a-z0-9][a-z0-9./:_-]*@sha256:[a-f0-9]{64}"))throw new IllegalArgumentException();
   Map<String,Object> raw=JsonMapper.builder().build().readValue(json,Map.class);
   var meta=(Map<String,Object>)raw.get("metadata");
   if(!"Deployment".equals(raw.get("kind"))||!"apps/v1".equals(raw.get("apiVersion"))||!namespace.equals(meta.get("namespace"))||!name.equals(meta.get("name")))throw new IllegalArgumentException();
   var spec=(Map<String,Object>)raw.get("spec");
   var pod=(Map<String,Object>)((Map<String,Object>)spec.get("template")).get("spec");
   var containers=(List<Map<String,Object>>)pod.get("containers");
   if(containers.size()!=1||!name.equals(containers.getFirst().get("name")))throw new IllegalArgumentException();
   // Do not carry literal environment values into a preview or stored artifact.
   for(String group:List.of("containers","initContainers"))
    for(var container:(List<Map<String,Object>>)pod.getOrDefault(group,List.of()))
     for(var env:(List<Map<String,Object>>)container.getOrDefault("env",List.of()))
      if(env.containsKey("value"))throw new IllegalArgumentException();
   containers.getFirst().put("image",image);
   var cleanMeta=new LinkedHashMap<String,Object>();cleanMeta.put("name",name);cleanMeta.put("namespace",namespace);
   if(meta.containsKey("labels"))cleanMeta.put("labels",meta.get("labels"));
   var document=new LinkedHashMap<String,Object>();document.put("apiVersion","apps/v1");document.put("kind","Deployment");document.put("metadata",cleanMeta);document.put("spec",spec);
   var options=new DumperOptions();options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
   return new Yaml(options).dump(document);
  }catch(Exception e){throw Errors.DEPLOYMENT_RUNTIME.exception();}
 }
}
