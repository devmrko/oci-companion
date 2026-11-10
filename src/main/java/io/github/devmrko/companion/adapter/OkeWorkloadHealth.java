package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Read-only integration with an administrator-controlled kubectl and kubeconfig. */
@Component
public class OkeWorkloadHealth {
    public record Target(String appId, String profile, String tenancy, String user, String region,
            String compartment, String clusterId, String kubeconfig, String namespace,
            String deployment, String server, String tlsServerName, boolean inspectNodes, String endpoint) {
        public Target(String appId,String profile,String tenancy,String user,String region,String compartment,String clusterId,String kubeconfig,String namespace,String deployment,String server,String tlsServerName,boolean inspectNodes){this(appId,profile,tenancy,user,region,compartment,clusterId,kubeconfig,namespace,deployment,server,tlsServerName,inspectNodes,"PUBLIC_ENDPOINT");}
        public Target(String appId,String profile,String tenancy,String user,String region,String compartment,String clusterId,String kubeconfig,String namespace,String deployment,String server,String tlsServerName){this(appId,profile,tenancy,user,region,compartment,clusterId,kubeconfig,namespace,deployment,server,tlsServerName,false);}
    }
    public record Pod(String name, String phase, String ready, int restarts, String reason, String node, String uid) {
        public Pod(String name,String phase,String ready,int restarts,String reason,String node){this(name,phase,ready,restarts,reason,node,"");}
        public Pod(String name,String phase,String ready,int restarts,String reason){this(name,phase,ready,restarts,reason,"");}
    }
    public record Node(String name,String ready,String diskPressure,String memoryPressure,String pidPressure,String networkUnavailable,boolean unschedulable,
            String architecture,String operatingSystem,String architectureLabel,String operatingSystemLabel) {
        public String platformState(){
            if((!"Unknown".equals(architecture)&&!"Unknown".equals(architectureLabel)&&!architecture.equals(architectureLabel))||
               (!"Unknown".equals(operatingSystem)&&!"Unknown".equals(operatingSystemLabel)&&!operatingSystem.equals(operatingSystemLabel)))return "불일치";
            if(List.of(architecture,operatingSystem,architectureLabel,operatingSystemLabel).contains("Unknown"))return "미확인";
            return "일치";
        }
    }
    public record NodeReport(String state,String guidance,String checkedAt,int total,int page,int last,List<Node> items) {}
    private static NodeReport disabledNodes(){return new NodeReport("미설정","관리자 옵션 inspectNodes와 nodes get 권한을 확인하세요.",Instant.now().toString(),0,0,0,List.of());}
    public record Warning(String pod,String reason,String guidance,String observedAt) {}
    public record Events(String state,String guidance,List<Warning> items) {}
    private static Events noEvents(){return new Events("미조회","경고 이벤트는 현재 장애 여부를 확정하지 않습니다.",List.of());}
    public record Status(String state, String namespace, String deployment, int desired,
            int ready, int updated, String checkedAt, String guidance, List<Pod> pods, NodeReport nodes, Events events) {
        public Status(String state,String namespace,String deployment,int desired,int ready,int updated,String checkedAt,String guidance,List<Pod> pods,NodeReport nodes){this(state,namespace,deployment,desired,ready,updated,checkedAt,guidance,pods,nodes,noEvents());}
        public Status(String state,String namespace,String deployment,int desired,int ready,int updated,String checkedAt,String guidance,List<Pod> pods){this(state,namespace,deployment,desired,ready,updated,checkedAt,guidance,pods,disabledNodes());}
    }
    private final Path config;
    private OkeConnectionStore managed;
    private final String kubectl;
    private final AgentConnectionRegistry registry;
    private final OciAgentDependencies cloud;
    private final JsonMapper json=JsonMapper.builder().build();

    public OkeWorkloadHealth(@Value("${companion.oke-workloads:.tools/oke-workloads.json}") String config,
            @Value("${companion.kubectl:/usr/local/bin/kubectl}") String kubectl,
            AgentConnectionRegistry registry, OciAgentDependencies cloud) {
        this.config=Path.of(config);this.kubectl=kubectl;this.registry=registry;this.cloud=cloud;
    }
    @org.springframework.beans.factory.annotation.Autowired
    public OkeWorkloadHealth(@Value("${companion.oke-workloads:.tools/oke-workloads.json}") String config,
            @Value("${companion.kubectl:/usr/local/bin/kubectl}") String kubectl,
            AgentConnectionRegistry registry,OciAgentDependencies cloud,OkeConnectionStore managed){
        this(config,kubectl,registry,cloud);this.managed=managed;
    }
    private Status empty(String state,String guidance) {
        return new Status(state,"","",0,0,0,Instant.now().toString(),guidance,List.of());
    }
    public Status check(Connection c,String region,String compartment,String appId) {return check(c,region,compartment,appId,0);}
    public Status check(Connection c,String region,String compartment,String appId,int nodePage) {
        try {
            var t=target(c,region,compartment,appId);
            if(t==null)return empty("미설정","OKE 조회 연결에서 클러스터와 앱을 선택하세요.");
            var deployment=read(t,"deployment",t.deployment());
            var sets=read(t,"replicasets",null);
            var pods=read(t,"pods",null);
            var summary=summarize(t,deployment,sets,pods);
            return withEvents(withNodes(summary,inspectNodes(t,summary.pods(),nodePage)),inspectEvents(t,summary.pods()));
        } catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            return empty("조회 불가","관리자 연결 설정·OCI 조회 권한·Kubernetes RBAC·API 터널을 확인하세요. 앱 중지로 단정할 수 없습니다.");
        }
    }
    Target target(Connection c,String region,String compartment,String appId)throws Exception {
            if(c.demo())throw new IllegalArgumentException();
            var app=registry.get(c,region,compartment,appId);
            var t=managed==null?null:managed.get(c,region,compartment,appId);
            if(t==null){
                if(!Files.exists(config))return null;
                if(Files.isSymbolicLink(config)||Files.size(config)>1_000_000)throw new IllegalArgumentException();
                var matches=Arrays.stream(json.readValue(Files.readString(config),Target[].class))
                    .filter(x->appId.equals(x.appId())&&c.profile().equals(x.profile())&&c.tenancy().equals(x.tenancy())
                        &&c.user().equals(x.user())&&region.equals(x.region())&&compartment.equals(x.compartment())).toList();
                if(matches.isEmpty())return null;
                if(matches.size()!=1)throw new IllegalArgumentException();t=matches.getFirst();
            }
            validate(t);final var target=t;
            var cluster=app.dependencies().stream().filter(d->d.type().equals("OKE")&&d.ocid().equals(target.clusterId())).findFirst().orElseThrow();
            if(!cloud.check(c,region,compartment,cluster).verified())throw new IllegalArgumentException();
            return t;
    }
    private static void validate(Target t) {
        for(String name:List.of(t.namespace(),t.deployment()))
            if(name.length()>253||!name.matches("[a-z0-9]([a-z0-9.-]*[a-z0-9])?"))throw new IllegalArgumentException();
        var path=Path.of(t.kubeconfig());
        if(!path.isAbsolute()||Files.isSymbolicLink(path)||!Files.isRegularFile(path))throw new IllegalArgumentException();
        if(t.server()!=null&&!t.server().isBlank()) {
            var uri=java.net.URI.create(t.server());
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException();
        }
        if(t.tlsServerName()!=null&&!t.tlsServerName().isBlank()&&!t.tlsServerName().matches("[a-zA-Z0-9.-]+"))throw new IllegalArgumentException();
    }
    JsonNode read(Target t,String resource,String name)throws Exception {
        return execute(t,name==null?List.of(resource):List.of(resource,name));
    }
    JsonNode readNodes(Target t,List<String> names)throws Exception {
        var arguments=new ArrayList<String>();arguments.add("nodes");arguments.addAll(names);return execute(t,arguments);
    }
    private JsonNode execute(Target t,List<String> resourceArguments)throws Exception {
        var args=new ArrayList<String>();args.add("get");args.addAll(resourceArguments);args.addAll(List.of("-o","json"));
        return json.readTree(command(t,args,2_000_000));
    }
    byte[] command(Target t,List<String> command,int limit)throws Exception {
        if(!Path.of(kubectl).isAbsolute())throw new IllegalArgumentException();
        var args=new ArrayList<>(List.of(kubectl,"--kubeconfig",t.kubeconfig(),"--namespace",t.namespace(),"--request-timeout=5s"));
        if(t.server()!=null&&!t.server().isBlank())args.add("--server="+t.server());
        if(t.tlsServerName()!=null&&!t.tlsServerName().isBlank())args.add("--tls-server-name="+t.tlsServerName());
        args.addAll(command);
        var process=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var output=executor.submit(()->{try(var in=process.getInputStream()){return in.readNBytes(limit+1);}});
            try {
                var bytes=output.get(8,TimeUnit.SECONDS);
                if(bytes.length>limit||!process.waitFor(1,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalArgumentException();
                return bytes;
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();output.cancel(true);
            }
        }
    }
    private static boolean owned(JsonNode node,String kind,Set<String> owners) {
        for(var ref:node.path("metadata").path("ownerReferences"))
            if(ref.path("controller").asBoolean(false)&&kind.equals(ref.path("kind").asText())&&owners.contains(ref.path("uid").asText()))return true;
        return false;
    }
    static Status summarize(Target t,JsonNode deployment,JsonNode sets,JsonNode podList) {
        if(!"Deployment".equals(deployment.path("kind").asText())
                ||!t.namespace().equals(deployment.path("metadata").path("namespace").asText())
                ||!t.deployment().equals(deployment.path("metadata").path("name").asText()))throw new IllegalArgumentException();
        String uid=deployment.path("metadata").path("uid").asText();
        if(uid.isBlank()||!sets.path("items").isArray()||!podList.path("items").isArray())throw new IllegalArgumentException();
        Set<String> rs=new HashSet<>();
        for(var item:sets.path("items"))if(t.namespace().equals(item.path("metadata").path("namespace").asText())&&owned(item,"Deployment",Set.of(uid)))rs.add(item.path("metadata").path("uid").asText());
        var pods=new ArrayList<Pod>();boolean podIssue=false;
        for(var item:podList.path("items")) {
            if(!t.namespace().equals(item.path("metadata").path("namespace").asText())||!owned(item,"ReplicaSet",rs))continue;
            var status=item.path("status");boolean ready=false;int restarts=0;
            for(var condition:status.path("conditions"))if("Ready".equals(condition.path("type").asText()))ready="True".equals(condition.path("status").asText());
            var reasons=new TreeSet<String>();
            for(String group:List.of("initContainerStatuses","containerStatuses"))for(var container:status.path(group)) {
                restarts+=container.path("restartCount").asInt(0);
                for(String state:List.of("waiting","terminated")){String reason=container.path("state").path(state).path("reason").asText("");if(!reason.isBlank())reasons.add(reason);}
            }
            boolean terminating=!item.path("metadata").path("deletionTimestamp").isMissingNode()&&!item.path("metadata").path("deletionTimestamp").isNull();
            if(!ready&&!terminating)podIssue=true;
            pods.add(new Pod(item.path("metadata").path("name").asText(),terminating?"Terminating":status.path("phase").asText("Unknown"),ready?"Ready":"NotReady",restarts,String.join(", ",reasons),item.path("spec").path("nodeName").asText(""),item.path("metadata").path("uid").asText("")));
        }
        pods.sort(Comparator.comparing(Pod::name));
        var status=deployment.path("status");int desired=deployment.path("spec").path("replicas").asInt(1),ready=status.path("readyReplicas").asInt(0),updated=status.path("updatedReplicas").asInt(0);
        boolean observed=status.path("observedGeneration").asLong(-1)>=deployment.path("metadata").path("generation").asLong(0);
        boolean failed=false;
        for(var condition:status.path("conditions"))if(("Progressing".equals(condition.path("type").asText())&&"False".equals(condition.path("status").asText()))||("ReplicaFailure".equals(condition.path("type").asText())&&"True".equals(condition.path("status").asText())))failed=true;
        String state=desired==0?"복제본 0개":failed?"배포 확인 필요":!observed?"변경 반영 대기":ready>=desired&&updated==desired&&status.path("availableReplicas").asInt(0)>=desired&&status.path("replicas").asInt(0)==desired&&!podIssue&&pods.size()==desired?"준비 완료":"배포 진행·준비 확인 필요";
        return new Status(state,t.namespace(),t.deployment(),desired,ready,updated,Instant.now().toString(),"Deployment·Pod 조회 시점의 상태입니다. 실제 요청 성공·로그·알람은 별도로 확인하세요. 노드 상태는 아래 별도 조회 결과를 확인하세요.",List.copyOf(pods));
    }
    private static Status withNodes(Status s,NodeReport nodes){return new Status(s.state(),s.namespace(),s.deployment(),s.desired(),s.ready(),s.updated(),s.checkedAt(),s.guidance(),s.pods(),nodes,s.events());}
    private static Status withEvents(Status s,Events events){return new Status(s.state(),s.namespace(),s.deployment(),s.desired(),s.ready(),s.updated(),s.checkedAt(),s.guidance(),s.pods(),s.nodes(),events);}
    JsonNode readEvents(Target t)throws Exception{return execute(t,List.of("events","--field-selector=type=Warning"));}
    Events inspectEvents(Target t,List<Pod> pods){
        if(pods.stream().noneMatch(p->!p.uid().isBlank()))return new Events("대상 Pod 없음","조회할 Pod가 없습니다.",List.of());
        try{return summarizeEvents(t.namespace(),pods,readEvents(t));}
        catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return new Events("조회 불가","Pod 결과는 유지됩니다. namespace의 events list RBAC와 API 연결을 확인하세요.",List.of());}
    }
    static Events summarizeEvents(String namespace,List<Pod> pods,JsonNode response){
        if(!response.path("items").isArray())throw new IllegalArgumentException();
        var owners=new HashMap<String,String>();for(var p:pods)if(!p.uid().isBlank())owners.put(p.uid(),p.name());
        var warnings=new ArrayList<Warning>();
        for(var e:response.path("items")){
            var ref=e.has("involvedObject")?e.path("involvedObject"):e.path("regarding");String pod=owners.get(ref.path("uid").asText());
            if(pod==null||!pod.equals(ref.path("name").asText())||!"Pod".equals(ref.path("kind").asText())||!namespace.equals(e.path("metadata").path("namespace").asText())||!namespace.equals(ref.path("namespace").asText())||!"Warning".equals(e.path("type").asText()))continue;
            String reason=e.path("reason").asText("Unknown");if(!reason.matches("[A-Za-z0-9]{1,80}"))reason="Unknown";
            String message=e.path("message").asText(e.path("note").asText("")).toLowerCase(Locale.ROOT),guidance;
            if(reason.equals("FailedScheduling")){
                var causes=new ArrayList<String>();if(message.contains("insufficient cpu"))causes.add("CPU 부족");if(message.contains("insufficient memory"))causes.add("메모리 부족");if(message.contains("untolerated taint"))causes.add("허용하지 않은 taint");if(message.contains("node affinity")||message.contains("node selector"))causes.add("노드 선택 조건 불일치");
                guidance=causes.isEmpty()?"배치 실패 · 자원 요청량·노드 조건·볼륨 바인딩을 확인하세요.":String.join(" · ",causes)+" · 당시 배치 실패 이벤트입니다.";
            }else if(message.contains("pull image")||message.contains("pulling image"))guidance="이미지 받기 실패 · 이미지 경로·인증·레지스트리 연결을 확인하세요.";
            else if(reason.equals("FailedMount")||reason.equals("FailedAttachVolume"))guidance="볼륨 연결 실패 · PVC·CSI·접근 권한을 확인하세요.";
            else if(reason.equals("Unhealthy"))guidance="상태 검사 실패 · startup/readiness/liveness 설정과 앱 응답을 확인하세요.";
            else if(reason.equals("BackOff"))guidance="재시도 대기 · 컨테이너 종료 이유와 로그를 확인하세요.";
            else guidance="Kubernetes 경고 · OCI 클러스터 액세스에서 해당 Pod 이벤트를 확인하세요.";
            String at=e.path("series").path("lastObservedTime").asText("");if(at.isBlank())at=e.path("lastTimestamp").asText("");if(at.isBlank())at=e.path("eventTime").asText("");if(at.isBlank())at=e.path("metadata").path("creationTimestamp").asText("");
            try{at=Instant.parse(at).toString();}catch(Exception ignored){at="미확인";}
            warnings.add(new Warning(pod,reason,guidance,at));
        }
        warnings.sort(Comparator.comparing((Warning w)->{try{return Instant.parse(w.observedAt());}catch(Exception e){return Instant.MIN;}}).reversed());
        return new Events("조회 완료","현재 앱 소유 Pod의 경고 이벤트 중 최근 최대 20개입니다. 보존 기간이 지난 이벤트는 없을 수 있으며, 현재 장애를 뜻하지는 않습니다. 원문 메시지는 표시·저장하지 않습니다.",warnings.stream().limit(20).toList());
    }
    NodeReport inspectNodes(Target t,List<Pod> pods,int requestedPage) {
        if(!t.inspectNodes())return disabledNodes();
        var names=pods.stream().map(Pod::node).filter(n->!n.isBlank()).distinct().sorted().toList();
        int last=Math.max(0,(names.size()-1)/20),page=Math.max(0,Math.min(requestedPage,last));
        if(names.isEmpty())return new NodeReport("할당 노드 없음","Pod가 아직 배치되지 않았거나 조회할 Pod가 없습니다.",Instant.now().toString(),0,0,0,List.of());
        var selected=names.subList(page*20,Math.min(names.size(),page*20+20));
        try {
            for(String name:selected)if(name.length()>253||!name.matches("[a-z0-9]([a-z0-9.-]*[a-z0-9])?"))throw new IllegalArgumentException();
            return summarizeNodes(readNodes(t,selected),selected,names.size(),page,last);
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return new NodeReport("조회 불가","Pod 조회 결과는 유지됩니다. nodes get RBAC·노드 존재 여부·API 연결을 확인하세요. NotReady로 단정하지 않습니다.",Instant.now().toString(),names.size(),page,last,List.of());}
    }
    private static String condition(JsonNode node,String type) {
        for(var c:node.path("status").path("conditions"))if(type.equals(c.path("type").asText())) {
            String value=c.path("status").asText();return Set.of("True","False").contains(value)?value:"Unknown";
        }
        return "Unknown";
    }
    private static String platform(JsonNode value){
        String text=value.asText("");return text.matches("[a-z0-9_]{1,32}")?text:"Unknown";
    }
    static NodeReport summarizeNodes(JsonNode response,List<String> selected,int total,int page,int last) {
        var items=new ArrayList<JsonNode>();
        if("Node".equals(response.path("kind").asText()))items.add(response);
        else if(response.path("items").isArray())response.path("items").forEach(items::add);
        else throw new IllegalArgumentException();
        var nodes=new TreeMap<String,Node>();
        for(var node:items) {
            String name=node.path("metadata").path("name").asText();
            if(!"Node".equals(node.path("kind").asText())||!selected.contains(name))continue;
            nodes.put(name,new Node(name,condition(node,"Ready"),condition(node,"DiskPressure"),condition(node,"MemoryPressure"),condition(node,"PIDPressure"),condition(node,"NetworkUnavailable"),node.path("spec").path("unschedulable").asBoolean(false),
                platform(node.path("status").path("nodeInfo").path("architecture")),platform(node.path("status").path("nodeInfo").path("operatingSystem")),
                platform(node.path("metadata").path("labels").path("kubernetes.io/arch")),platform(node.path("metadata").path("labels").path("kubernetes.io/os"))));
        }
        return new NodeReport(nodes.size()==selected.size()?"조회 완료":"일부 조회됨","Ready=True는 준비됨, 압박·NetworkUnavailable=True는 확인이 필요합니다. Unknown은 확인되지 않은 값입니다. 디스크 여유 공간·메모리 사용률 측정은 아닙니다.",Instant.now().toString(),total,page,last,List.copyOf(nodes.values()));
    }

}
