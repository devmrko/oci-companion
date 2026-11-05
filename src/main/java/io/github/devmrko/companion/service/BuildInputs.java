package io.github.devmrko.companion.service;
import io.github.devmrko.companion.port.BuildControl.Parameter;
import io.github.devmrko.companion.port.BuildInputStore.Rule;
import java.util.*;
public final class BuildInputs {
    private BuildInputs() {}
    public record Field(String name,String value,String defaultValue,String description,String type,String requirement,List<String> choices) {
        public boolean required() { return requirement.equals("required"); }
        public String badge() { return required()?"필수":requirement.equals("optional")?"선택":"필수 여부 미지정"; }
    }
    public static List<Field> fields(List<Parameter> current,List<Parameter> values,List<Rule> rules) {
        if(!current.stream().map(Parameter::name).collect(java.util.stream.Collectors.toSet()).equals(values.stream().map(Parameter::name).collect(java.util.stream.Collectors.toSet())))
            throw Errors.BUILD_PRESET.exception();
        var result=new ArrayList<Field>();
        for(var p:current) {
            var rule=rules.stream().filter(r->r.name().equals(p.name())).findFirst().orElse(new Rule(p.name(),"text","unspecified",List.of()));
            var value=values.stream().filter(v->v.name().equals(p.name())).findFirst().orElse(p).value();
            result.add(new Field(p.name(),value,p.value(),p.description(),rule.type(),rule.requirement(),rule.choices()));
        }
        return result;
    }
    public static void validate(List<Field> fields) {
        for(var f:fields) {
            if(f.required() && f.value().isBlank()) throw Errors.BUILD_REQUIRED.exception();
            if(f.type().equals("select") && !f.value().isEmpty() && !f.choices().contains(f.value())) throw Errors.BUILD_PARAMETERS.exception();
        }
    }
    public static List<Rule> rules(List<Parameter> parameters,List<String> names,List<String> types,List<String> requirements,List<String> options) {
        if(names==null || types==null || requirements==null || options==null || names.size()!=parameters.size() || names.size()!=types.size() || names.size()!=requirements.size() || names.size()!=options.size()) throw Errors.BUILD_PARAMETERS.exception();
        var result=new ArrayList<Rule>();var seen=new HashSet<String>();
        for(int i=0;i<names.size();i++) {
            var name=names.get(i);
            if(!seen.add(name) || parameters.stream().noneMatch(p->p.name().equals(name)) || !Set.of("text","select").contains(types.get(i)) || !Set.of("unspecified","optional","required").contains(requirements.get(i))) throw Errors.BUILD_PARAMETERS.exception();
            if(options.get(i).length()>8192) throw Errors.BUILD_PARAMETERS.exception();
            var choices=options.get(i).lines().map(String::trim).filter(v->!v.isEmpty()).distinct().toList();
            if(types.get(i).equals("select") && (choices.isEmpty() || choices.size()>100)) throw Errors.BUILD_PARAMETERS.exception();
            result.add(new Rule(name,types.get(i),requirements.get(i),choices));
        }
        return result;
    }
}
