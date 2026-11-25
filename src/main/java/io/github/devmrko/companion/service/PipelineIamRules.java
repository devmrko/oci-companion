package io.github.devmrko.companion.service;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative recognition: unknown or conditional grants are never treated as proven access. */
public final class PipelineIamRules {
    private PipelineIamRules() {}
    public static final List<String> REQUIRED=List.of("manage devops-family", "manage repos", "read all-artifacts",
        "use ons-topics", "manage cluster-family", "use virtual-network-family");
    public record Group(String id, String name, String rule) {}
    private static final Pattern GRANT=Pattern.compile("(?i)^allow dynamic-group (id )?([^ ]+) to (inspect|read|use|manage) ([a-z-]+) in (tenancy|compartment (?:id )?[^ ]+)$");
    public static String rule(String compartment) {
        return "ALL {resource.compartment.id = '"+compartment+"', ANY {resource.type = 'devopsbuildpipeline', resource.type = 'devopsdeploypipeline'}}";
    }
    public static boolean matches(String rule, String pipeline, String compartment) {
        String value=rule.strip();
        var combined=Pattern.compile("(?is)^(ALL|ANY)\\s*\\{(.*)}$").matcher(value);
        if(combined.matches()) {
            var parts=new ArrayList<String>(); int depth=0,start=0;
            String body=combined.group(2);
            for(int i=0;i<body.length();i++) {
                char ch=body.charAt(i); if(ch=='{')depth++; if(ch=='}')depth--;
                if(depth<0)return false;
                if(ch==',' && depth==0){parts.add(body.substring(start,i));start=i+1;}
            }
            if(depth!=0)return false;parts.add(body.substring(start));
            return combined.group(1).equalsIgnoreCase("ALL")?parts.stream().allMatch(p->matches(p,pipeline,compartment)):parts.stream().anyMatch(p->matches(p,pipeline,compartment));
        }
        var atom=Pattern.compile("(?i)^(resource.type|resource.id|resource.compartment.id)\\s*=\\s*'([^']+)'$").matcher(value);
        if(!atom.matches())return false;
        String type=pipeline.startsWith("ocid1.devopsdeploypipeline.")?"devopsdeploypipeline":"devopsbuildpipeline";
        String actual=switch(atom.group(1).toLowerCase(Locale.ROOT)){case "resource.type"->type;case "resource.id"->pipeline;default->compartment;};
        return actual.equals(atom.group(2));
    }
    public static List<String> missing(List<Group> groups,List<String> statements,Set<String> ancestorScopes,String pipeline,String compartment,String targetGroup,String scope) {
        var matching=groups.stream().filter(g->matches(g.rule(),pipeline,compartment)).toList();
        return REQUIRED.stream().filter(required->statements.stream().noneMatch(statement->{
            var grant=GRANT.matcher(statement.strip().replaceAll("\\s+"," "));
            if(!grant.matches())return false;
            if(matching.stream().noneMatch(g->grant.group(2).equals(grant.group(1)==null?g.name():g.id())))return false;
            String s=grant.group(5);
            if(!s.equalsIgnoreCase("tenancy")&&!ancestorScopes.contains(s.replaceFirst("(?i)^compartment (?:id )?","")))return false;
            String[] need=required.split(" ");
            return List.of("inspect","read","use","manage").indexOf(grant.group(3).toLowerCase(Locale.ROOT))>=List.of("inspect","read","use","manage").indexOf(need[0])
                && (grant.group(4).equalsIgnoreCase(need[1])||grant.group(4).equalsIgnoreCase("all-resources"));
        })).map(r->"Allow dynamic-group "+targetGroup+" to "+r+" "+scope).toList();
    }
    public static String safe(String message) {
        return Objects.toString(message, "").replaceAll("ocid1\\.[A-Za-z0-9._-]+", "[OCID]")
            .replaceAll("(?i)(?:[0-9a-f]{2}:){15}[0-9a-f]{2}","[FINGERPRINT]")
            .replaceAll("(?i)(authorization|token|password|secret)\\s*[:=]\\s*[^\\s,]+", "$1=[REDACTED]");
    }
    public static String stageStatus(String run,String stage) {
        return "FAILED".equals(run) && (stage==null||Set.of("ACCEPTED","IN_PROGRESS").contains(stage))?"FAILED":Objects.toString(stage,"—");
    }
}
