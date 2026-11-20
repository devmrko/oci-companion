package io.github.devmrko.companion.service;

import io.github.devmrko.companion.port.BuildHistory;
import java.time.*;
import java.util.*;

/** Derived from native OCI execution timestamps; no inferred finish time or local history. */
public final class BuildInsights {
    private BuildInsights() {}
    public record Summary(int total,long succeeded,long failed,long canceled,long other,int timed,String median) {}
    public record Stage(String name,String type,String logStage,String currentState,String previousState,String currentDuration,String previousDuration,String difference) {}
    public static Long seconds(String start,String end) {
        try {long n=Duration.between(Instant.parse(start),Instant.parse(end)).getSeconds();return n<0?null:n;}
        catch(RuntimeException e){return null;}
    }
    public static String duration(String start,String end){return format(seconds(start,end));}
    public static String format(Long seconds){return seconds==null?"기록 없음":seconds/60+"분 "+seconds%60+"초";}
    public static String difference(String a,String b,String c,String d){
        Long current=seconds(a,b),previous=seconds(c,d);
        if(current==null||previous==null)return "비교 불가";
        long delta=current-previous;return delta==0?"동일":(delta>0?"+":"−")+format(Math.abs(delta));
    }
    public static boolean terminal(String state){return Set.of("SUCCEEDED","FAILED","CANCELED").contains(state);}
    public static List<BuildHistory.Run> window(List<BuildHistory.Run> runs,Instant now,int days){
        Instant from=now.minus(Duration.ofDays(days));
        return runs.stream().filter(r->{try{var t=Instant.parse(r.createdAt());return !t.isBefore(from)&&!t.isAfter(now);}catch(RuntimeException e){return false;}})
            .sorted(Comparator.comparing(BuildHistory.Run::createdAt).reversed().thenComparing(BuildHistory.Run::id)).toList();
    }
    public static Summary summarize(List<BuildHistory.Run> runs){
        long ok=runs.stream().filter(r->r.state().equals("SUCCEEDED")).count(),bad=runs.stream().filter(r->r.state().equals("FAILED")).count(),cancel=runs.stream().filter(r->r.state().equals("CANCELED")).count();
        var times=runs.stream().filter(r->terminal(r.state())).map(r->seconds(r.startedAt(),r.finishedAt())).filter(Objects::nonNull).sorted().toList();
        Long median=times.isEmpty()?null:times.size()%2==1?times.get(times.size()/2):times.get(times.size()/2-1)+(times.get(times.size()/2)-times.get(times.size()/2-1))/2;
        return new Summary(runs.size(),ok,bad,cancel,runs.size()-ok-bad-cancel,times.size(),format(median));
    }
    public static List<Stage> stages(BuildHistory.Detail current,BuildHistory.Detail previous){
        var result=new ArrayList<Stage>();var before=previous==null?List.<BuildHistory.Step>of():previous.steps();
        var remaining=new ArrayList<>(before);
        for(var step:current.steps()){
            var matches=remaining.stream().filter(s->Objects.equals(s.stageId(),step.stageId())&&s.type().equals(step.type())&&s.name().equals(step.name())).toList();
            var match=matches.size()==1?matches.getFirst():null;if(match!=null)remaining.remove(match);
            result.add(new Stage(step.name(),step.type(),current.steps().stream().filter(s->s.stageId().equals(step.stageId())&&!s.type().equals("COMMAND")).map(BuildHistory.Step::name).findFirst().orElse(step.name()),step.state(),match==null?"기록 없음":match.state(),step.duration(),match==null?"기록 없음":match.duration(),match==null?"비교 불가":difference(step.startedAt(),step.finishedAt(),match.startedAt(),match.finishedAt())));
        }
        for(var step:remaining)result.add(new Stage(step.name(),step.type(),"","기록 없음",step.state(),"기록 없음",step.duration(),"비교 불가"));
        return result;
    }
}
