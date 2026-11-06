package io.github.devmrko.companion.adapter;

import com.sun.net.httpserver.HttpServer;
import io.github.devmrko.companion.domain.Models.Connection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class HttpAgentMonitoringTest {
    @TempDir Path temp;
    final Connection connection=new Connection("DEFAULT","tenant","user","home","label",false);
    final AtomicInteger calls=new AtomicInteger();
    HttpAgentMonitoring configured(String url) throws Exception {
        var token=temp.resolve("token"); Files.writeString(token,"private-test-token");
        var config=new HttpAgentMonitoring.Config("desk","DEFAULT","tenant","user","seoul","comp",url,token.toString());
        var path=temp.resolve("config.json"); Files.writeString(path,JsonMapper.builder().build().writeValueAsString(config));
        return new HttpAgentMonitoring(path.toString());
    }
    HttpServer server(int status,String traces) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            calls.incrementAndGet();
            assertEquals("Bearer private-test-token",exchange.getRequestHeaders().getFirst("Authorization"));
            String body=exchange.getRequestURI().getPath().equals("/health")?"{\"status\":\"UP\",\"version\":\"0.2.0\"}":traces;
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location","http://127.0.0.1:1/secret");
            exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        }); server.start(); return server;
    }
    @Test void displaysOnlyWhitelistedFieldsAndChecksScopeBeforeNetwork() throws Exception {
        var server=server(200,"[{\"request_id\":\"request-1\",\"status\":\"ok\",\"app_version\":\"0.2.0\",\"message\":\"sensitive-input\",\"model\":{\"model\":\"flash-lite\",\"input_tokens\":30},\"tools\":[{\"tool\":\"search\",\"stage\":\"execute\",\"status\":\"ok\"}]}]");
        try {
            var adapter=configured("http://127.0.0.1:"+server.getAddress().getPort());
            assertEquals("범위 불일치",adapter.snapshot(connection,"other","comp").status());
            assertEquals(0,calls.get());
            var result=adapter.snapshot(connection,"seoul","comp");
            assertEquals("UP",result.status()); assertEquals("0.2.0",result.version());
            assertEquals("request-1",result.traces().getFirst().requestId());
            assertEquals("30",result.traces().getFirst().inputTokens());
            assertTrue(result.traces().getFirst().tools().contains("search / execute / ok"));
            assertFalse(result.toString().contains("sensitive-input"));
            assertFalse(result.toString().contains("private-test-token"));
        } finally {server.stop(0);}
    }
    @Test void rejectsRemoteEndpointsAndRedirectsWithoutExposingProviderErrors() throws Exception {
        for(String url: new String[]{"http://169.254.169.254", "http://localhost:8093", "http://user@127.0.0.1:8093", "http://127.0.0.1:8093/path"})
            assertThrows(IllegalArgumentException.class,()->HttpAgentMonitoring.checkedBase(url));
        var server=server(302,"provider-private-error");
        try {
            var result=configured("http://127.0.0.1:"+server.getAddress().getPort()).snapshot(connection,"seoul","comp");
            assertEquals("조회 실패",result.status()); assertEquals(1,calls.get());
            assertFalse(result.toString().contains("provider-private-error"));
        } finally {server.stop(0);}
    }
    @Test void rejectsOversizedResponse() throws Exception {
        var server=server(200,"x".repeat(2_000_001));
        try {assertEquals("조회 실패",configured("http://127.0.0.1:"+server.getAddress().getPort()).snapshot(connection,"seoul","comp").status());}
        finally {server.stop(0);}
    }
    @Test void readsVersionedCapabilitiesWithoutClaimingHealth() throws Exception {
        var server=server(200,"[]");
        server.createContext("/api/capabilities", exchange->{
            assertEquals("Bearer private-test-token",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes="{\"schema_version\":1,\"app_version\":\"0.2.0\",\"capabilities\":{\"memory\":{\"status\":\"configured\",\"secret\":\"private-value\"},\"browser\":{\"status\":\"not_implemented\"}}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        });
        try {
            var result=configured("http://127.0.0.1:"+server.getAddress().getPort()).snapshot(connection,"seoul","comp");
            assertEquals("UP",result.status());
            assertEquals(9,result.capabilities().size());
            assertEquals("설정됨",result.capabilities().get(1).status());
            assertEquals("별도 검증 필요",result.capabilities().get(1).health());
            assertEquals("미구현",result.capabilities().get(8).status());
            assertFalse(result.toString().contains("private-value"));
        } finally {server.stop(0);}
    }
    @Test void capabilityVersionMismatchDoesNotHideExistingHealth() throws Exception {
        var server=server(200,"[]");
        server.createContext("/api/capabilities",exchange->{
            byte[] bytes="{\"schema_version\":1,\"app_version\":\"new-version\",\"capabilities\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        });
        try {
            var result=configured("http://127.0.0.1:"+server.getAddress().getPort()).snapshot(connection,"seoul","comp");
            assertEquals("UP",result.status());
            assertTrue(result.capabilities().isEmpty());
            assertTrue(result.capabilityNotice().contains("확인하지 못했습니다"));
        } finally {server.stop(0);}
    }

}
