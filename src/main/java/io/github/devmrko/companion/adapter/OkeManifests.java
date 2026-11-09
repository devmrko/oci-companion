package io.github.devmrko.companion.adapter;

import com.oracle.bmc.devops.model.*;
import io.github.devmrko.companion.service.Errors;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Bounded target inspection, not a replacement for Kubernetes admission policies. */
final class OkeManifests {
    static List<String> inspect(List<DeployArtifact> artifacts,String compartment,String project,String namespace){
        if(namespace==null||!namespace.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")||Set.of("default","kube-system","kube-public","kube-node-lease").contains(namespace))throw Errors.INVALID.exception();
        var images=new LinkedHashSet<String>();var names=new HashSet<String>();int count=0;int bytes=0;
        for(var artifact:artifacts){
            if(!compartment.equals(artifact.getCompartmentId())||!project.equals(artifact.getProjectId()))throw Errors.SCOPE.exception();
            if(artifact.getDeployArtifactType()!=DeployArtifact.DeployArtifactType.KubernetesManifest||artifact.getArgumentSubstitutionMode()!=DeployArtifact.ArgumentSubstitutionMode.None||!(artifact.getDeployArtifactSource() instanceof InlineDeployArtifactSource source))throw Errors.INVALID.exception();
            byte[] content=source.getBase64EncodedContent();
            if(content==null||(bytes+=content.length)>524288)throw Errors.INVALID.exception();
            var options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);options.setCodePointLimit(524288);options.setNestingDepthLimit(30);
            try {
                for(var raw:new Yaml(new SafeConstructor(options)).loadAll(new String(content,StandardCharsets.UTF_8))){
                    if(++count>100)throw Errors.INVALID.exception();
                    var doc=map(raw);var kind=text(doc,"kind");var meta=map(doc.get("metadata"));var name=text(meta,"name");
                    String api=kind.equals("Deployment")?"apps/v1":"v1";
                    if(!api.equals(doc.get("apiVersion"))||!Set.of("Namespace","ServiceAccount","PersistentVolumeClaim","Deployment","Service").contains(kind)||!names.add(kind+"/"+name))throw Errors.INVALID.exception();
                    if(kind.equals("Namespace")){
                        if(!namespace.equals(name)||meta.containsKey("namespace"))throw Errors.SCOPE.exception();
                    } else if(!namespace.equals(meta.get("namespace")))throw Errors.SCOPE.exception();
                    if(kind.equals("Deployment")){
                        var pod=map(map(map(doc.get("spec")).get("template")).get("spec"));
                        containers(pod.get("containers"),images);
                        if(pod.containsKey("initContainers"))containers(pod.get("initContainers"),images);
                        if(pod.containsKey("ephemeralContainers"))throw Errors.INVALID.exception();
                    }
                }
            }catch(Exception e){throw Errors.INVALID.exception();}
        }
        if(images.isEmpty())throw Errors.INVALID.exception();
        return List.copyOf(images);
    }
    private static Map<?,?> map(Object value){if(!(value instanceof Map<?,?> m))throw Errors.INVALID.exception();return m;}
    private static String text(Map<?,?> map,String key){if(!(map.get(key) instanceof String s)||s.isBlank())throw Errors.INVALID.exception();return s;}
    private static void containers(Object value,Set<String> images){
        if(!(value instanceof List<?> list)||list.isEmpty())throw Errors.INVALID.exception();
        for(var item:list){String image=text(map(item),"image");if(!image.matches("[a-z0-9][a-z0-9./:_-]*@sha256:[a-f0-9]{64}"))throw Errors.INVALID.exception();images.add(image);}
    }
}
