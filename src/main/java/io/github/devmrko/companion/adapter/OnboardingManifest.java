package io.github.devmrko.companion.adapter;

import java.util.*;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.devmrko.companion.service.Errors;

/** Clone only a reviewed workload; never copy a Service's allocated addresses or NodePort. */
final class OnboardingManifest {
 record Document(String yaml,String image,int replicas,int port){}
 static Document clone(String live,String namespace,String original,String name){
  OciOkeWorkloadConnections.name(name);if(original.equals(name))throw Errors.CONFLICT.exception();
  var json=JsonMapper.builder().build();var raw=json.readTree(live);String image=raw.path("spec").path("template").path("spec").path("containers").get(0).path("image").asText();
  String clean=ExistingOkeDeployment.render(live,namespace,original,image);
  @SuppressWarnings("unchecked") Map<String,Object> map=new Yaml().load(clean);
  var doc=(ObjectNode)json.valueToTree(map);var metadata=(ObjectNode)doc.path("metadata");metadata.put("name",name);metadata.set("labels",json.valueToTree(Map.of("app",name)));
  var spec=(ObjectNode)doc.path("spec");spec.set("selector",json.valueToTree(Map.of("matchLabels",Map.of("app",name))));
  var template=(ObjectNode)spec.path("template");template.set("metadata",json.valueToTree(Map.of("labels",Map.of("app",name))));
  var container=(ObjectNode)template.path("spec").path("containers").get(0);container.put("name",name);
  int port=container.path("ports").path(0).path("containerPort").asInt(0);if(port<1||port>65535)throw Errors.DEPLOYMENT_INPUT.exception();
  var service=Map.of("apiVersion","v1","kind","Service","metadata",Map.of("name",name,"namespace",namespace),"spec",Map.of("type","ClusterIP","selector",Map.of("app",name),"ports",List.of(Map.of("port",port,"targetPort",port,"protocol","TCP"))));
  return new Document(json.writeValueAsString(doc)+"\n---\n"+json.writeValueAsString(service),image,spec.path("replicas").asInt(1),port);
 }
}
