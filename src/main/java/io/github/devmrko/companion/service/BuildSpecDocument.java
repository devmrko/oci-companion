package io.github.devmrko.companion.service;

import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Edit native fields only. Unknown fields and steps remain in the YAML document. */
public final class BuildSpecDocument {
    public record Command(String path,String name,String value) {}
    public record Fields(List<Command> commands,Map<String,String> variables,Map<String,String> vaultVariables) {}
    private static Map<String,Object> parse(String text) {
        try {
            var options=new LoaderOptions();options.setCodePointLimit(131072);options.setMaxAliasesForCollections(20);options.setAllowDuplicateKeys(false);
            Object value=new Yaml(new SafeConstructor(options)).load(text);
            if(!(value instanceof Map<?,?> root)||!"build".equals(root.get("component"))||!(root.get("steps") instanceof List<?>))throw Errors.INVALID.exception();
            @SuppressWarnings("unchecked") var result=(Map<String,Object>)copy(root,new IdentityHashMap<>(),new int[]{50000},0);return result;
        }catch(Exception e){throw Errors.INVALID.exception();}
    }
    private static Object copy(Object value,IdentityHashMap<Object,Boolean> active,int[] budget,int depth){
        if(--budget[0]<0||depth>64)throw Errors.INVALID.exception();
        if(!(value instanceof Map<?,?>)&&!(value instanceof List<?>))return value;
        if(active.put(value,true)!=null)throw Errors.INVALID.exception();
        try{
            if(value instanceof Map<?,?> map){var result=new LinkedHashMap<Object,Object>();for(var entry:map.entrySet())result.put(entry.getKey(),copy(entry.getValue(),active,budget,depth+1));return result;}
            var result=new ArrayList<Object>();for(var item:(List<?>)value)result.add(copy(item,active,budget,depth+1));return result;
        }finally{active.remove(value);}
    }
    private static void commands(Object value,String path,List<Command> result,Map<String,Map<String,Object>> nodes) {
        if(path.length()>200)throw Errors.INVALID.exception();
        if(!(value instanceof List<?> steps))return;
        for(int i=0;i<steps.size();i++)if(steps.get(i) instanceof Map<?,?> raw) {
            @SuppressWarnings("unchecked") var step=(Map<String,Object>)raw;String key=path+"."+i;
            if("Command".equals(step.get("type"))&&step.get("command") instanceof String command){
                result.add(new Command(key,Objects.toString(step.get("name"),key),command));nodes.put(key,step);
            }
            commands(step.get("onFailure"),key+".onFailure",result,nodes);
        }
    }
    private static Map<String,String> variables(Map<String,Object> root,String key) {
        if(!(root.get("env") instanceof Map<?,?> env)||!env.containsKey(key))return Map.of();
        if(!(env.get(key) instanceof Map<?,?> values))throw Errors.INVALID.exception();
        var result=new LinkedHashMap<String,String>();
        for(var e:values.entrySet()){if(!(e.getKey() instanceof String)||e.getValue() instanceof Collection<?>||e.getValue() instanceof Map<?,?>)throw Errors.INVALID.exception();result.put(e.getKey().toString(),Objects.toString(e.getValue(),""));}
        return result;
    }
    public static Fields read(String text){var root=parse(text);var commands=new ArrayList<Command>();commands(root.get("steps"),"steps",commands,new LinkedHashMap<>());return new Fields(commands,variables(root,"variables"),variables(root,"vaultVariables"));}
    public static String update(String text,Fields edits) {
        var original=read(text);if(original.equals(edits))return text;
        if(edits==null||edits.commands()==null||edits.variables()==null||edits.vaultVariables()==null)throw Errors.INVALID.exception();
        var root=parse(text);var commands=new ArrayList<Command>();var nodes=new LinkedHashMap<String,Map<String,Object>>();commands(root.get("steps"),"steps",commands,nodes);
        var seen=new HashSet<String>();
        for(var command:edits.commands()){
            if(command==null||!nodes.containsKey(command.path())||!seen.add(command.path())||command.value()==null||command.value().isBlank()||command.value().length()>65536||command.value().indexOf('\0')>=0)throw Errors.INVALID.exception();
            nodes.get(command.path()).put("command",command.value());
        }
        if(seen.size()!=nodes.size())throw Errors.INVALID.exception();
        for(var key:edits.variables().keySet())if(edits.vaultVariables().containsKey(key))throw Errors.INVALID.exception();
        replaceVariables(root,"variables",original.variables(),edits.variables(),false);
        replaceVariables(root,"vaultVariables",original.vaultVariables(),edits.vaultVariables(),true);
        var options=new DumperOptions();options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        String result=new Yaml(options).dump(root);if(result.length()>131072)throw Errors.INVALID.exception();return result;
    }
    private static void replaceVariables(Map<String,Object> root,String key,Map<String,String> old,Map<String,String> values,boolean vault){
        if(old.equals(values))return;
        if(values.size()>200)throw Errors.INVALID.exception();
        for(var entry:values.entrySet())if(!entry.getKey().matches("[A-Za-z_][A-Za-z0-9_]*")||entry.getValue()==null||entry.getValue().length()>8192||entry.getValue().indexOf('\0')>=0||(vault&&!entry.getValue().matches("ocid1\\.vaultsecret\\.[A-Za-z0-9.]+")))throw Errors.INVALID.exception();
        @SuppressWarnings("unchecked") var env=(Map<String,Object>)root.computeIfAbsent("env",k->new LinkedHashMap<String,Object>());
        env.put(key,new LinkedHashMap<>(values));
    }
}
