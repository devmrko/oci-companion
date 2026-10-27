package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface BuildAutomation {
    record Trigger(String id,String name,String provider,String state,String conditions) {}
    record Created(String id,String url,String secret) {}
    record Dispatcher(String id,String name,String application,String pipeline) {}
    record Schedule(String id,String name,String expression,String state,String next,String last,String result,String etag) {}
    List<Trigger> triggers(Connection c,String region,String compartment,String pipeline);
    Created createTrigger(Connection c,String region,String compartment,String pipeline,String sourceKey,String name,String event,String branch,String token);
    List<Dispatcher> dispatchers(Connection c,String region,String compartment,String pipeline);
    List<Schedule> schedules(Connection c,String region,String compartment,String pipeline);
    String createSchedule(Connection c,String region,String compartment,String pipeline,String function,String name,String cron,String token);
    void scheduleState(Connection c,String region,String compartment,String pipeline,String id,String etag,boolean active);
}
