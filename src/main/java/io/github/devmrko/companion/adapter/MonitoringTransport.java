package io.github.devmrko.companion.adapter;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
final class MonitoringTransport {
 private final JsonMapper mapper=JsonMapper.builder().build();
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    static URI checkedBase(String value) {
        URI uri = URI.create(value);
        if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
            || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException("Invalid configured endpoint");
        return uri;
    }
    JsonNode read(URI uri, String token) throws Exception {
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
