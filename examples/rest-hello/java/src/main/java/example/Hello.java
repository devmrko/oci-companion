package example;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
public class Hello {
 public static void main(String[] args) throws Exception {
  var server=HttpServer.create(new InetSocketAddress("0.0.0.0",8080),0);
  server.createContext("/", exchange -> {
   int status=!exchange.getRequestMethod().equals("GET")?405:!exchange.getRequestURI().getPath().equals("/hello")?404:200;
   String body=status==200?"{\"message\":\"hello\",\"language\":\"java\"}":status==404?"{\"error\":\"not found\"}":"{\"error\":\"method not allowed\"}";
   byte[] data=body.getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,data.length);
   try(var out=exchange.getResponseBody()){out.write(data);}
  });server.start();
 }
}
