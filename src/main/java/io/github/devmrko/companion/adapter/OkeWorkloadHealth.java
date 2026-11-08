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
            String deployment, String server, String tlsServerName) {}
    public record Pod(String name, String phase, String ready, int restarts, String reason) {}
    public record Status(String state, String namespace, String deployment, int desired,
            int ready, int updated, String checkedAt, String guidance, List<Pod> pods) {}
    private final Path config;
    private final String kubectl;
    private final AgentConnectionRegistry registry;
    private final OciAgentDependencies cloud;
    private final JsonMapper json=JsonMapper.builder().build();

    public OkeWorkloadHealth(@Value("${companion.oke-workloads:.tools/oke-workloads.json}") String config,
            @Value("${companion.kubectl:/usr/local/bin/kubectl}") String kubectl,
            AgentConnectionRegistry registry, OciAgentDependencies cloud) {
        this.config=Path.of(config);this.kubectl=kubectl;this.registry=registry;this.cloud=cloud;
    }
    private Status empty(String state,String guidance) {
        return new Status(state,"","",0,0,0,Instant.now().toString(),guidance,List.of());
    }
    public Status check(Connection c,String region,String compartment,String appId) {
        try {
            if(c.demo())throw new IllegalArgumentException();
            var app=registry.get(c,region,compartment,appId);
            if(!Files.exists(config))return empty("미설정","관리자가 앱별 Kubernetes 조회 연결을 설정해야 합니다.");
            if(Files.isSymbolicLink(config)||Files.size(config)>1_000_000)throw new IllegalArgumentException();
            var matches=Arrays.stream(json.readValue(Files.readString(config),Target[].class))
                .filter(t->appId.equals(t.appId())&&c.profile().equals(t.profile())&&c.tenancy().equals(t.tenancy())
                    &&c.user().equals(t.user())&&region.equals(t.region())&&compartment.equals(t.compartment())).toList();
            if(matches.isEmpty())return empty("미설정","현재 앱·사용자·리전·구획에 맞는 Kubernetes 조회 연결이 없습니다.");
            if(matches.size()!=1)throw new IllegalArgumentException();
            var t=matches.getFirst();validate(t);
            var cluster=app.dependencies().stream().filter(d->d.type().equals("OKE")&&d.ocid().equals(t.clusterId())).findFirst().orElseThrow();
            if(!cloud.check(c,region,compartment,cluster).verified())throw new IllegalArgumentException();
            var deployment=read(t,"deployment",t.deployment());
            var sets=read(t,"replicasets",null);
            var pods=read(t,"pods",null);
            return summarize(t,deployment,sets,pods);
        } catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            return empty("조회 불가","관리자 연결 설정·OCI 조회 권한·Kubernetes RBAC·API 터널을 확인하세요. 앱 중지로 단정할 수 없습니다.");
        }
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
        if(!Path.of(kubectl).isAbsolute())throw new IllegalArgumentException();
        var args=new ArrayList<>(List.of(kubectl,"--kubeconfig",t.kubeconfig(),"--namespace",t.namespace(),"--request-timeout=5s"));
        if(t.server()!=null&&!t.server().isBlank()){args.add("--server="+t.server());}
        if(t.tlsServerName()!=null&&!t.tlsServerName().isBlank()){args.add("--tls-server-name="+t.tlsServerName());}
        args.add("get");args.add(resource);if(name!=null)args.add(name);args.add("-o");args.add("json");
        var process=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var output=executor.submit(()->{try(var in=process.getInputStream()){return in.readNBytes(2_000_001);}});
            try {
                var bytes=output.get(8,TimeUnit.SECONDS);
                if(bytes.length>2_000_000||!process.waitFor(1,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalArgumentException();
                return json.readTree(bytes);
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
            pods.add(new Pod(item.path("metadata").path("name").asText(),terminating?"Terminating":status.path("phase").asText("Unknown"),ready?"Ready":"NotReady",restarts,String.join(", ",reasons)));
        }
        pods.sort(Comparator.comparing(Pod::name));
        var status=deployment.path("status");int desired=deployment.path("spec").path("replicas").asInt(1),ready=status.path("readyReplicas").asInt(0),updated=status.path("updatedReplicas").asInt(0);
        boolean observed=status.path("observedGeneration").asLong(-1)>=deployment.path("metadata").path("generation").asLong(0);
        boolean failed=false;
        for(var condition:status.path("conditions"))if(("Progressing".equals(condition.path("type").asText())&&"False".equals(condition.path("status").asText()))||("ReplicaFailure".equals(condition.path("type").asText())&&"True".equals(condition.path("status").asText())))failed=true;
        String state=desired==0?"복제본 0개":failed?"배포 확인 필요":!observed?"변경 반영 대기":ready>=desired&&updated==desired&&status.path("availableReplicas").asInt(0)>=desired&&status.path("replicas").asInt(0)==desired&&!podIssue&&pods.size()==desired?"준비 완료":"배포 진행·준비 확인 필요";
        return new Status(state,t.namespace(),t.deployment(),desired,ready,updated,Instant.now().toString(),"Deployment·Pod 조회 시점의 상태입니다. 실제 요청 성공·노드 Ready·로그·알람은 별도로 확인하세요.",List.copyOf(pods));
    }
}
