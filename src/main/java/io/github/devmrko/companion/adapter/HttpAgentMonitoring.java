package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.AgentMonitoring;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class HttpAgentMonitoring implements AgentMonitoring {
    public record Config(String name, String profile, String tenancy, String user, String region,
                         String compartment, String baseUrl, String tokenFile) {}
    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public HttpAgentMonitoring(@Value("${companion.agent-monitor-config:.tools/agent-monitor.json}") String file) {
        this.file = Path.of(file);
    }
    static URI checkedBase(String value) {
        URI uri = URI.create(value);
        if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
            || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException("Invalid configured endpoint");
        return uri;
    }
    public Snapshot snapshot(Connection c, String region, String compartment) {
        if (!Files.exists(file)) return new Snapshot("에이전트", "미설정", "—", List.of(), "조회할 앱 연결을 설정하세요.");
        try {
            if (Files.size(file) > 16384) throw new IllegalArgumentException("Invalid config");
            Config cfg = mapper.readValue(Files.readString(file), Config.class);
            if (!Objects.equals(cfg.profile(), c.profile()) || !Objects.equals(cfg.tenancy(), c.tenancy())
                || !Objects.equals(cfg.user(), c.user()) || !Objects.equals(cfg.region(), region)
                || !Objects.equals(cfg.compartment(), compartment))
                return new Snapshot("에이전트", "범위 불일치", "—", List.of(), "현재 연결·리전·구획에 등록된 앱이 없습니다.");
            URI base = checkedBase(cfg.baseUrl());
            Path tokenPath = Path.of(cfg.tokenFile());
            if (Files.size(tokenPath) > 8192) throw new IllegalArgumentException("Invalid credential");
            String token = Files.readString(tokenPath).strip();
            if (token.isBlank() || token.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid credential");
            JsonNode health = read(base.resolve("/health"), token);
            JsonNode records = read(base.resolve("/api/traces"), token);
            if (!records.isArray()) throw new IllegalArgumentException("Invalid trace response");
            var traces = new ArrayList<Trace>();
            for (int i = Math.max(0, records.size()-100); i < records.size(); i++) {
                JsonNode row = records.get(i), model = row.path("model");
                var steps = new ArrayList<String>();
                if (row.path("tools").isArray()) for (JsonNode step : row.path("tools"))
                    steps.add(text(step,"tool")+" / "+text(step,"stage")+" / "+text(step,"status"));
                traces.add(new Trace(text(row,"request_id"), text(row,"status"), text(row,"app_version"),
                    text(row,"mode"),text(model,"model"),text(model,"region"),text(model,"input_tokens"),
                    text(model,"output_tokens"),text(row,"elapsed_ms"),String.join("; ",steps),text(row,"error_code")));
            }
            Collections.reverse(traces);
            var capabilities = new ArrayList<Capability>();
            String capabilityNotice="앱이 보고한 구현·설정 상태입니다. 외부 서비스 연결 성공을 뜻하지 않습니다.";
            try {
                JsonNode report=read(base.resolve("/api/capabilities"),token);
                if(report.path("schema_version").asInt()!=1 || !text(report,"app_version").equals(text(health,"version"))
                    || !report.path("capabilities").isObject()) throw new IllegalArgumentException("Unsupported capability contract");
                for(String key:List.of("runtime","memory","gateway","identity","policy","observability","evaluations","code_interpreter","browser")) {
                    String state=text(report.path("capabilities").path(key),"status");
                    String label=switch(state) {
                        case "partial" -> "일부 구현";
                        case "configured" -> "설정됨";
                        case "not_configured" -> "미설정";
                        case "not_implemented" -> "미구현";
                        default -> "알 수 없음";
                    };
                    capabilities.add(new Capability(key,label,"별도 검증 필요"));
                }
            } catch(Exception failure) {
                if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
                capabilityNotice="기능 상태 API를 확인하지 못했습니다. 미지원·버전 변경·연결 상태를 확인하세요.";
            }
            return new Snapshot(cfg.name(), text(health,"status"), text(health,"version"), List.copyOf(traces),
                "설정된 앱 계정의 최대 100건 · 수동 조회 · 대화 원문은 표시하지 않습니다.",
                List.copyOf(capabilities),capabilityNotice);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Snapshot("에이전트", "조회 실패", "—", List.of(), "앱 상태·SSH 터널·인증 설정을 확인하세요.");
        }
    }
    private String text(JsonNode node, String key) {
        String value = node.path(key).asText("—");
        return value.length() <= 500 ? value : value.substring(0,500);
    }
    private JsonNode read(URI uri, String token) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
            .header("Authorization", "Bearer "+token).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode()!=200) throw new IllegalStateException("Upstream unavailable");
            var reading = new FutureTask<byte[]>(() -> body.readNBytes(2_000_001));
            Thread.ofVirtual().start(reading);
            byte[] bytes;
            try { bytes = reading.get(8, TimeUnit.SECONDS); }
            finally { reading.cancel(true); }
            if (bytes.length>2_000_000) throw new IllegalStateException("Response limit");
            return mapper.readTree(bytes);
        }
    }
}
