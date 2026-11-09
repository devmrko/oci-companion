package io.github.devmrko.companion.service;
import io.github.devmrko.companion.port.DeploymentSetup.Input;
import java.util.*;
import org.yaml.snakeyaml.*;
public final class DeploymentSpecifications {
    private DeploymentSpecifications(){}
    public static String render(Input i){
        if(i==null||java.util.stream.Stream.of(i.environment(),i.repository(),i.artifact(),i.runAs(),i.stop(),i.install(),i.start(),i.health(),i.recovery()).anyMatch(Objects::isNull))throw Errors.DEPLOYMENT_SETUP.exception();
        if(i.name()==null||i.name().isBlank()||i.name().length()>200||i.description()==null||i.description().length()>400||i.timeout()<60||i.timeout()>3600||i.batch()<1||i.batch()>50)throw Errors.DEPLOYMENT_SETUP.exception();
        if(i.destination()==null||!i.destination().matches("/[A-Za-z0-9_./-]{1,200}")||i.destination().equals("/")||i.destination().contains("//")||Arrays.asList(i.destination().split("/",-1)).contains("..")||!i.runAs().matches("[a-z_][a-z0-9_-]{0,31}"))throw Errors.DEPLOYMENT_SETUP.exception();
        if(i.install().isBlank()||i.health().isBlank())throw Errors.DEPLOYMENT_SETUP.exception();
        var vars=variables(i.variables(),false);var vault=variables(i.vaultVariables(),true);
        if(vars.containsKey("DEPLOY_DIR")||vault.containsKey("DEPLOY_DIR")||vars.keySet().stream().anyMatch(vault::containsKey))throw Errors.DEPLOYMENT_SETUP.exception();vars.put("DEPLOY_DIR",i.destination());
        var spec=new LinkedHashMap<String,Object>();spec.put("version","1.0");spec.put("component","deployment");spec.put("runAs",i.runAs());spec.put("shell","bash");spec.put("timeoutInSeconds",i.timeout());
        var env=new LinkedHashMap<String,Object>();env.put("variables",vars);if(!vault.isEmpty())env.put("vaultVariables",vault);spec.put("env",env);spec.put("files",List.of(Map.of("source","/","destination",i.destination())));
        var steps=new ArrayList<Map<String,Object>>();for(var pair:List.of(new String[]{"Stop",i.stop()},new String[]{"Install",i.install()},new String[]{"Start",i.start()},new String[]{"Health check",i.health()})){
            if(pair[1].isBlank())continue;var step=step(pair[0],pair[1],i.timeout(),i.runAs());if(!i.recovery().isBlank())step.put("onFailure",List.of(step("Recovery",i.recovery(),i.timeout(),i.runAs())));steps.add(step);
        }spec.put("steps",steps);var options=new DumperOptions();options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);String yaml=new Yaml(options).dump(spec);if(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768)throw Errors.DEPLOYMENT_SETUP.exception();return yaml;
    }
    private static Map<String,Object> step(String name,String command,int timeout,String runAs){if(command.length()>8192||command.indexOf('\0')>=0)throw Errors.DEPLOYMENT_SETUP.exception();var step=new LinkedHashMap<String,Object>();step.put("stepType","Command");step.put("name",name);step.put("command","set -e\n"+command);step.put("runAs",runAs);step.put("timeoutInSeconds",timeout);return step;}
    private static LinkedHashMap<String,String> variables(String text,boolean vault){
        if(text==null||text.length()>8192)throw Errors.DEPLOYMENT_SETUP.exception();var map=new LinkedHashMap<String,String>();
        for(String line:text.split("\\R")){if(line.isBlank())continue;int equals=line.indexOf('=');if(equals<1)throw Errors.DEPLOYMENT_SETUP.exception();String key=line.substring(0,equals).strip(),value=line.substring(equals+1);if(!key.matches("[A-Za-z_][A-Za-z0-9_]*")||key.startsWith("OCI_DEVOPS")||map.containsKey(key)||value.indexOf('\0')>=0||(vault&&!value.matches("ocid1\\.vaultsecret\\.[A-Za-z0-9._-]+")))throw Errors.DEPLOYMENT_SETUP.exception();map.put(key,value);}
        if(map.size()>50)throw Errors.DEPLOYMENT_SETUP.exception();return map;
    }
}
