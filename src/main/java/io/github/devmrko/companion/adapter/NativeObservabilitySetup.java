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
  new Provider("pinpoint","Pinpoint","https://github.com/pinpoint-apm/pinpoint-kubernetes","https://github.com/pinpoint-apm/pinpoint-kubernetes/blob/master/docs/AGENTS.md","공식 Helm 차트 4.0.1 평가 구성: x86 노드·약 8.3GiB 메모리 요청·영구 볼륨 필요. OKE 설치를 확인했습니다. Java 실시간 진단은 Pinpoint 에이전트를 사용하세요."));}
 public Settings get(Connection c,String r,String comp,String app){return registry.get(c,r,comp,app).observability();}
 public java.util.Map<String,String> installationFiles(String provider){
  var files=switch(provider){case "signoz"->List.of("casting.yaml","install.sh","README.md");case "pinpoint"->List.of("prepare.sh","values-placement.yaml","registry-renderer.sh","README.md","OPERATIONS.md");default->throw new IllegalArgumentException("SigNoz 또는 Pinpoint를 선택하세요.");};
  var result=new java.util.LinkedHashMap<String,String>();
  try{for(String name:files)try(var input=new org.springframework.core.io.ClassPathResource("observability/"+provider+"/"+name).getInputStream()){result.put(name,new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
  catch(java.io.IOException e){throw new IllegalArgumentException("설치 구성을 읽지 못했습니다.");}
  return java.util.Collections.unmodifiableMap(result);
 }
 public void save(Connection c,String r,String comp,String app,Settings settings){if(c.demo())throw new IllegalArgumentException("실제 프로파일로 연결하세요.");validate(settings);registry.observability(c,r,comp,app,settings);}
 public static void validate(Settings s){
  if(s==null||s.provider()==null||s.runtime()==null||!Set.of("grafana","signoz","pinpoint").contains(s.provider())||!Set.of("java","python","java-pinpoint").contains(s.runtime()))throw new IllegalArgumentException("제품과 계측 방식을 선택하세요.");
  checkedUrl(s.dashboard());
  if(s.runtime().equals("java-pinpoint")){
   if(s.endpoint()==null||s.endpoint().length()>1000)throw new IllegalArgumentException("수집 주소를 확인하세요.");
   if(!s.provider().equals("pinpoint")||s.agentId()==null||!s.agentId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,23}")||s.service()==null||s.service().length()>24)throw new IllegalArgumentException("Pinpoint Java: 서비스명과 인스턴스별 Agent Name를 24자 이내로 입력하세요.");
   try{var u=URI.create(s.endpoint());if(!"grpc".equals(u.getScheme())||u.getHost()==null||u.getPort()!=-1||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!u.getRawPath().isEmpty())throw new IllegalArgumentException();}catch(Exception e){throw new IllegalArgumentException("Pinpoint 수집 주소는 grpc://호스트 형식입니다. 기본 포트 9991·9992·9993을 사용합니다.");}
  }else checkedUrl(s.endpoint());
  if(s.service()==null||!s.service().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))throw new IllegalArgumentException("서비스명은 영문·숫자·점·밑줄·하이픈 100자 이내입니다.");
 }
 private static void checkedUrl(String value){
  try{var u=URI.create(value);if(value.length()>1000||!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||value.contains("\\")||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException();}
  catch(Exception e){throw new IllegalArgumentException("HTTP(S) 주소를 입력하세요. 인증정보·쿼리·앵커는 제외하세요.");}
 }
 public String instrumentation(Settings s){validate(s);
  if(s.runtime().equals("java-pinpoint"))return "# Pinpoint agent 4.0.1을 /opt/pinpoint에 설치. 기존 JVM 옵션에 추가.\nJAVA_TOOL_OPTIONS=-javaagent:/opt/pinpoint/pinpoint-bootstrap.jar -Dpinpoint.serviceName=DEFAULT -Dpinpoint.applicationName="+s.service()+" -Dpinpoint.agentName="+s.agentId()+" -Dpinpoint.profiler.profiles.active=release -Dprofiler.transport.grpc.collector.ip="+URI.create(s.endpoint()).getHost()+"\n# Collector TCP: 9991(metadata), 9992(statistics), 9993(spans)\n# OpenTelemetry Java agent와 중복 적용하지 마세요.\n";
  return "OTEL_SERVICE_NAME="+s.service()+"\nOTEL_EXPORTER_OTLP_ENDPOINT="+s.endpoint()+"\nOTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf\n"+
  (s.provider().equals("pinpoint")?"OTEL_METRICS_EXPORTER=none\nOTEL_LOGS_EXPORTER=none\n# Pinpoint 4.x OTLP 추적 수신 주소. 메트릭·덤프는 별도 구성.\n":"")+
  (s.runtime().equals("java")?"# OpenTelemetry Java agent를 별도로 설치한 뒤 기존 JVM 옵션에 추가\nJAVA_TOOL_OPTIONS=-javaagent:/opt/otel/opentelemetry-javaagent.jar\n":"# 앱의 가상환경에서 실행\n# pip install opentelemetry-distro opentelemetry-exporter-otlp\n# opentelemetry-bootstrap -a install\n# 기존 실행 명령 앞에 opentelemetry-instrument 추가\n");}
}
