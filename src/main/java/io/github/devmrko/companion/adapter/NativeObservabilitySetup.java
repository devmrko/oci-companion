package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ObservabilitySetup;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NativeObservabilitySetup implements ObservabilitySetup {
 private final AgentConnectionRegistry registry;
 public NativeObservabilitySetup(AgentConnectionRegistry registry){this.registry=registry;}
 public List<Provider> providers(){return List.of(
  new Provider("grafana","Grafana","https://grafana.com/docs/grafana/latest/setup-grafana/installation/","https://grafana.com/docs/grafana-cloud/send-data/otlp/","화면과 수집 저장소는 별도입니다. Cloud 또는 메트릭·로그·추적 저장소와 Collector를 준비하세요."),
  new Provider("signoz","SigNoz","https://signoz.io/docs/install/","https://signoz.io/docs/instrumentation/","VM은 공식 Foundry 설치, OKE는 공식 Kubernetes 구성을 확인하세요. 영구 볼륨과 보존 기간을 설정하세요."),
  new Provider("pinpoint","Pinpoint","https://pinpoint-apm.gitbook.io/pinpoint/getting-started/installation","https://pinpoint-apm.gitbook.io/pinpoint/getting-started/installation","Collector·Web·저장소를 함께 준비하세요. 기존 pinpoint-docker 저장소는 보관 상태입니다. Java 실시간 요청·스레드 진단은 Pinpoint 에이전트와 별도 실시간 설정이 필요합니다."));}
 public Settings get(Connection c,String r,String comp,String app){return registry.get(c,r,comp,app).observability();}
 public void save(Connection c,String r,String comp,String app,Settings settings){if(c.demo())throw new IllegalArgumentException("실제 프로파일로 연결하세요.");validate(settings);registry.observability(c,r,comp,app,settings);}
 public static void validate(Settings s){
  if(s==null||!Set.of("grafana","signoz","pinpoint").contains(s.provider())||!Set.of("java","python").contains(s.runtime()))throw new IllegalArgumentException("제품과 언어를 선택하세요.");
  checkedUrl(s.dashboard());checkedUrl(s.endpoint());
  if(s.service()==null||!s.service().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))throw new IllegalArgumentException("서비스명은 영문·숫자·점·밑줄·하이픈 100자 이내입니다.");
 }
 private static void checkedUrl(String value){
  try{var u=URI.create(value);if(value.length()>1000||!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||value.contains("\\")||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException();}
  catch(Exception e){throw new IllegalArgumentException("HTTP(S) 주소를 입력하세요. 인증정보·쿼리·앵커는 제외하세요.");}
 }
 public String instrumentation(Settings s){validate(s);return "OTEL_SERVICE_NAME="+s.service()+"\nOTEL_EXPORTER_OTLP_ENDPOINT="+s.endpoint()+"\nOTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf\n"+
  (s.provider().equals("pinpoint")?"OTEL_METRICS_EXPORTER=none\nOTEL_LOGS_EXPORTER=none\n# Pinpoint 4.x OTLP 추적 수신 주소. 메트릭·덤프는 별도 구성.\n":"")+
  (s.runtime().equals("java")?"# OpenTelemetry Java agent를 별도로 설치한 뒤 기존 JVM 옵션에 추가\nJAVA_TOOL_OPTIONS=-javaagent:/opt/otel/opentelemetry-javaagent.jar\n":"# 앱의 가상환경에서 실행\n# pip install opentelemetry-distro opentelemetry-exporter-otlp\n# opentelemetry-bootstrap -a install\n# 기존 실행 명령 앞에 opentelemetry-instrument 추가\n");}
}
