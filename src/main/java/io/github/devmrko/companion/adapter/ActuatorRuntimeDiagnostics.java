package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.RuntimeDiagnostics;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ActuatorRuntimeDiagnostics implements RuntimeDiagnostics {
 private final AgentConnectionRegistry registry;
 private final MonitoringTransport transport=new MonitoringTransport();
 private static final Map<String,String> METRICS=Map.of("process.cpu.usage","프로세스 CPU 비율","jvm.memory.used","JVM 메모리 사용량 (Heap+Non-heap)","jvm.threads.live","플랫폼 스레드 수","jvm.gc.pause","GC 시간·횟수","http.server.requests","HTTP 요청 누적 통계");
 public ActuatorRuntimeDiagnostics(AgentConnectionRegistry registry){this.registry=registry;}
 private JsonNode read(Connection c,String r,String comp,AgentConnectionRegistry.Registration app,String path)throws Exception{return transport.read(MonitoringTransport.checkedBase(app.baseUrl()).resolve(path),registry.token(c,r,comp,app));}
 public Snapshot inspect(Connection c,String r,String comp,String id){
  var app=registry.get(c,r,comp,id);var caps=new ArrayList<Capability>();var metrics=new ArrayList<Metric>();
  if(!"spring-actuator-v1".equals(app.adapter())){
   for(String name:List.of("런타임 메트릭","스레드·스택 덤프"))caps.add(new Capability(name,"PREPARE","미연결","Java: Actuator 연결 / Python: 별도 수집 어댑터 필요"));
  }else{
   boolean available=false;Set<String> names=new HashSet<>();
   try{var index=read(c,r,comp,app,"/actuator/metrics");if(!index.path("names").isArray())throw new IllegalArgumentException();for(var n:index.path("names"))names.add(n.asText());available=true;}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();}
   if(available){
    try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
     var jobs=new LinkedHashMap<String,Future<List<Metric>>>();
     for(String name:new TreeSet<>(METRICS.keySet()))if(names.contains(name))jobs.put(name,pool.submit(()->parseMetric(name,read(c,r,comp,app,"/actuator/metrics/"+name))));
     for(var entry:jobs.entrySet())try{metrics.addAll(entry.getValue().get());}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();}
    }
   }
   caps.add(new Capability("런타임 메트릭",metrics.isEmpty()?"UNKNOWN":"PASS","Spring Boot Actuator",metrics.isEmpty()?"수집값 없음 · metrics 노출·인증·앱 트래픽 확인":"현재 스냅샷 · 일부 항목은 미제공될 수 있음"));
   boolean advertised=false;
   try{var index=read(c,r,comp,app,"/actuator");advertised=index.path("_links").has("threaddump");}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();}
   caps.add(new Capability("스레드 덤프",advertised?"CONFIGURED":"UNKNOWN","Actuator threaddump",advertised?"엔드포인트 안내 확인 · 실행 시 권한·실제 응답 검증":"미확인 · discovery·threaddump 노출·인증 확인"));
  }
  caps.add(new Capability("CPU·할당·락 프로파일","PREPARE","Pyroscope / JFR / Python profiler","별도 연결 필요 · 언어·수집 방식별 지원 차이"));
  caps.add(new Capability("전체 힙 덤프","UNSUPPORTED","Java HPROF","이 화면에서 수집하지 않음 · Python 할당 스냅샷과 다름"));
  caps.add(new Capability("p95·p99·오류율 추이","PREPARE","OpenTelemetry + APM / Prometheus","시계열·히스토그램 연결 필요 · 누적값으로 대체하지 않음"));
  return new Snapshot(app.name(),"spring-actuator-v1".equals(app.adapter())?"Java / Spring Boot":"미확인",Instant.now(),List.copyOf(caps),List.copyOf(metrics));
 }
 static List<Metric> parseMetric(String name,JsonNode n){
  if(!name.equals(n.path("name").asText())||!n.path("measurements").isArray())throw new IllegalArgumentException();var rows=new ArrayList<Metric>();
  for(var m:n.path("measurements")){String stat=m.path("statistic").asText();if(!Set.of("VALUE","COUNT","TOTAL_TIME","MAX","TOTAL").contains(stat)||!m.path("value").isNumber())continue;double value=m.path("value").asDouble();if(!Double.isFinite(value)||value<0)continue;String unit=stat.equals("COUNT")?"count":n.path("baseUnit").asText("");if(unit.length()>40)throw new IllegalArgumentException();if(name.equals("jvm.memory.used")){value/=1048576d;unit="MiB";}if(name.equals("process.cpu.usage")){value*=100;unit="%";}rows.add(new Metric(METRICS.get(name),stat,value,unit,Instant.now()));}return rows;
 }
 public Dump threadDump(Connection c,String r,String comp,String id){var app=registry.get(c,r,comp,id);if(!"spring-actuator-v1".equals(app.adapter()))throw new IllegalArgumentException("Spring Boot Actuator 연결이 필요합니다.");try{return parseDump(app.name(),read(c,r,comp,app,"/actuator/threaddump"));}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalArgumentException("덤프 조회 실패 · threaddump 노출·인증·응답 크기(2MB)를 확인하세요.");}}
 static Dump parseDump(String app,JsonNode n){var threads=n.path("threads");if(!threads.isArray()||threads.isEmpty()||threads.size()>2000)throw new IllegalArgumentException();var out=new StringBuilder();for(var t:threads){String state=t.path("threadState").asText();if(!Set.of("NEW","RUNNABLE","BLOCKED","WAITING","TIMED_WAITING","TERMINATED").contains(state)||!t.path("threadId").isIntegralNumber()||!t.path("stackTrace").isArray())throw new IllegalArgumentException();out.append(clean(t.path("threadName").asText())).append(" #").append(t.path("threadId").asLong()).append(" ").append(state).append('\n');if(!t.path("lockName").isNull()&&!t.path("lockName").isMissingNode())out.append("  lock: ").append(clean(t.path("lockName").asText())).append(" owner: ").append(t.path("lockOwnerId").asLong(-1)).append('\n');for(var frame:t.path("stackTrace")){out.append("  at ").append(clean(frame.path("className").asText())).append('.').append(clean(frame.path("methodName").asText())).append('(').append(clean(frame.path("fileName").asText("Unknown"))).append(':').append(frame.path("lineNumber").asInt(-1)).append(")\n");}out.append('\n');if(out.length()>2_000_000)throw new IllegalArgumentException();}return new Dump(app,Instant.now(),out.toString(),threads.size());}
 private static String clean(String s){String value=s.replaceAll("[\\p{Cntrl}]"," ");return value.substring(0,Math.min(value.length(),500));}
}
