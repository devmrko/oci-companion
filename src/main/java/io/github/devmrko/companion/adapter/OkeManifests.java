package io.github.devmrko.companion.adapter;

import com.oracle.bmc.devops.model.*;
import io.github.devmrko.companion.service.Errors;
import java.nio.charset.StandardCharsets;
import java.util.*;
import io.github.devmrko.companion.port.DeploymentRuns.Job;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Bounded target inspection, not a replacement for Kubernetes admission policies. */
final class OkeManifests {
    record Inspection(List<String> images,List<Job> jobs) {}
    static List<String> inspect(List<DeployArtifact> artifacts,String compartment,String project,String namespace){
        return inspectPlan(artifacts,compartment,project,namespace).images();
    }
    static Inspection inspectPlan(List<DeployArtifact> artifacts,String compartment,String project,String namespace){
        if(namespace==null||!namespace.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")||Set.of("default","kube-system","kube-public","kube-node-lease").contains(namespace))throw Errors.INVALID.exception();
        var jobs=new ArrayList<Job>();var images=new LinkedHashSet<String>();var names=new HashSet<String>();int count=0;int bytes=0;
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
                    String api=kind.equals("Deployment")?"apps/v1":kind.equals("Job")?"batch/v1":"v1";
                    if(!api.equals(doc.get("apiVersion"))||!Set.of("Namespace","ServiceAccount","PersistentVolumeClaim","Deployment","Service","Job").contains(kind)||!names.add(kind+"/"+name))throw Errors.INVALID.exception();
                    if(kind.equals("Namespace")){
                        if(!namespace.equals(name)||meta.containsKey("namespace"))throw Errors.SCOPE.exception();
                    } else if(!namespace.equals(meta.get("namespace")))throw Errors.SCOPE.exception();
                    if(kind.equals("Job"))jobs.add(job(name,map(doc.get("spec"))));
                    if(kind.equals("Deployment")||kind.equals("Job")){
                        var pod=map(map(map(doc.get("spec")).get("template")).get("spec"));
                        containers(pod.get("containers"),images);
                        if(pod.containsKey("initContainers"))containers(pod.get("initContainers"),images);
                        if(pod.containsKey("ephemeralContainers"))throw Errors.INVALID.exception();
                    }
                }
            }catch(Exception e){throw Errors.INVALID.exception();}
        }
        if(images.isEmpty())throw Errors.INVALID.exception();
        return new Inspection(List.copyOf(images),List.copyOf(jobs));
    }
    private static Job job(String name,Map<?,?> spec){
        dns(name);
        int deadline=integer(spec,"activeDeadlineSeconds",1,600),retries=integer(spec,"backoffLimit",0,2),ttl=integer(spec,"ttlSecondsAfterFinished",60,86400);
        for(String key:List.of("parallelism","completions"))if(spec.containsKey(key))integer(spec,key,1,1);
        for(String key:List.of("manualSelector","suspend"))if(spec.containsKey(key)&&!Boolean.FALSE.equals(spec.get(key)))throw Errors.INVALID.exception();
        for(String key:List.of("podFailurePolicy","successPolicy","backoffLimitPerIndex","managedBy","selector"))if(spec.containsKey(key))throw Errors.INVALID.exception();
        if(spec.containsKey("completionMode")&&!"NonIndexed".equals(spec.get("completionMode")))throw Errors.INVALID.exception();
        var pod=map(map(spec.get("template")).get("spec"));
        String account=text(pod,"serviceAccountName");dns(account);if("default".equals(account)||!"Never".equals(pod.get("restartPolicy")))throw Errors.INVALID.exception();
        for(String key:List.of("hostNetwork","hostPID","hostIPC","shareProcessNamespace"))if(pod.containsKey(key)&&!Boolean.FALSE.equals(pod.get(key)))throw Errors.INVALID.exception();
        for(String key:List.of("nodeName","ephemeralContainers","hostAliases"))if(pod.containsKey(key))throw Errors.INVALID.exception();
        var security=map(pod.get("securityContext"));
        if(!Boolean.TRUE.equals(security.get("runAsNonRoot")))throw Errors.INVALID.exception();
        if(security.containsKey("runAsUser"))integer(security,"runAsUser",1,Integer.MAX_VALUE);
        var refs=new TreeSet<String>();
        for(String key:List.of("containers","initContainers"))if(pod.containsKey(key)){
            if(!(pod.get(key) instanceof List<?> cs)||cs.isEmpty()||cs.size()>4)throw Errors.INVALID.exception();
            for(var raw:cs){
                var c=map(raw);var sc=map(c.get("securityContext"));
                if(!Boolean.FALSE.equals(sc.get("allowPrivilegeEscalation"))||!Boolean.TRUE.equals(sc.get("readOnlyRootFilesystem"))||Boolean.TRUE.equals(sc.get("privileged"))||Boolean.FALSE.equals(sc.get("runAsNonRoot")))throw Errors.INVALID.exception();
                if(sc.containsKey("runAsUser"))integer(sc,"runAsUser",1,Integer.MAX_VALUE);
                var caps=map(sc.get("capabilities"));
                if(caps.containsKey("add")||!(caps.get("drop") instanceof List<?> drops)||!drops.contains("ALL"))throw Errors.INVALID.exception();
                var limits=map(map(c.get("resources")).get("limits"));
                if(!text(limits,"cpu").matches("(?:[1-9][0-9]{0,2}m|1000m|1)")||!text(limits,"memory").matches("(?:[1-9][0-9]{0,2}Mi|1024Mi|1Gi)"))throw Errors.INVALID.exception();
                if(c.containsKey("ports"))throw Errors.INVALID.exception();
                secretRefs(c,refs);
            }
        }
        if(pod.containsKey("volumes")){
            if(!(pod.get("volumes") instanceof List<?> volumes)||volumes.size()>16)throw Errors.INVALID.exception();
            for(var raw:volumes){var v=map(raw);
                if(v.size()!=2||!v.containsKey("name")||(!v.containsKey("emptyDir")&&!v.containsKey("secret")&&!v.containsKey("configMap")&&!v.containsKey("projected")))throw Errors.INVALID.exception();
                if(v.containsKey("emptyDir")&&!text(map(v.get("emptyDir")),"sizeLimit").matches("[1-9][0-9]{0,2}Mi"))throw Errors.INVALID.exception();
                if(v.containsKey("secret")){String ref=text(map(v.get("secret")),"secretName");dns(ref);refs.add(ref);}
                if(v.containsKey("projected")){
                    var sources=map(v.get("projected")).get("sources");if(!(sources instanceof List<?> list))throw Errors.INVALID.exception();
                    for(var source:list){var x=map(source);if(x.size()!=1||!x.containsKey("secret"))throw Errors.INVALID.exception();String ref=text(map(x.get("secret")),"name");dns(ref);refs.add(ref);}
                }
            }
        }
        return new Job(name,account,deadline,retries,ttl,List.copyOf(refs));
    }
    private static void secretRefs(Object value,Set<String> refs){
        if(value instanceof Map<?,?> m)for(var entry:m.entrySet()){
            if(Set.of("secretKeyRef","secretRef").contains(entry.getKey())){String name=text(map(entry.getValue()),"name");dns(name);refs.add(name);}
            else secretRefs(entry.getValue(),refs);
        } else if(value instanceof List<?> list)for(var x:list)secretRefs(x,refs);
    }
    private static void dns(String value){if(value.length()>63||!value.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))throw Errors.INVALID.exception();}
    private static int integer(Map<?,?> m,String key,int min,int max){Object value=m.get(key);if(!(value instanceof Integer n)||n<min||n>max)throw Errors.INVALID.exception();return n;}
    private static Map<?,?> map(Object value){if(!(value instanceof Map<?,?> m))throw Errors.INVALID.exception();return m;}
    private static String text(Map<?,?> map,String key){if(!(map.get(key) instanceof String s)||s.isBlank())throw Errors.INVALID.exception();return s;}
    private static void containers(Object value,Set<String> images){
        if(!(value instanceof List<?> list)||list.isEmpty())throw Errors.INVALID.exception();
        for(var item:list){String image=text(map(item),"image");if(!image.matches("[a-z0-9][a-z0-9./:_-]*@sha256:[a-f0-9]{64}"))throw Errors.INVALID.exception();images.add(image);}
    }
}
