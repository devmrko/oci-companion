package io.github.devmrko.companion.service;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.support.CronExpression;
public final class BuildSchedules {
    private BuildSchedules() {}
    public static String cron(String value) {
        String text=value.strip();
        // Resource Scheduler UNIX cron; restrict to portable numeric five-field syntax.
        if(text.length()>100 || !text.matches("[0-9*,/ -]+") || text.split("\\s+").length!=5)throw Errors.BUILD_STAGE_INPUT.exception();
        String[] fields=text.split("\\s+");
        // Avoid the different AND/OR semantics of day-of-month plus day-of-week.
        if(!fields[2].equals("*") && !fields[4].equals("*"))throw Errors.BUILD_STAGE_INPUT.exception();
        try {CronExpression.parse("0 "+text);}catch(IllegalArgumentException e){throw Errors.BUILD_STAGE_INPUT.exception();}
        return text;
    }
    public static List<String> next(String expression,Instant now) {
        var cron=CronExpression.parse("0 "+cron(expression));var cursor=now.atZone(ZoneOffset.UTC);var result=new ArrayList<String>();
        for(int i=0;i<3;i++){cursor=cron.next(cursor);if(cursor==null)break;result.add(cursor.toInstant()+" · 서울 "+cursor.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime());}return result;
    }
}
