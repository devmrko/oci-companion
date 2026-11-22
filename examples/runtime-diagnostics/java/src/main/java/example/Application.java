package example;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
@SpringBootApplication @RestController
public class Application {
 public static void main(String[] args){SpringApplication.run(Application.class,args);}
 @GetMapping("/hello") String hello(){return "hello";}
 @GetMapping("/diagnostics/slow") org.springframework.http.ResponseEntity<String> slow()throws InterruptedException{
  if(!Boolean.parseBoolean(System.getenv("DIAGNOSTICS_SLOW_ENABLED")))return org.springframework.http.ResponseEntity.notFound().build();
  Thread.sleep(30_000);return org.springframework.http.ResponseEntity.ok("completed");
 }
 @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{
  String token=System.getenv("DIAGNOSTICS_TOKEN");if(token==null||token.length()<32)throw new IllegalStateException("DIAGNOSTICS_TOKEN must contain at least 32 characters");
  byte[] expected=("Bearer "+token).getBytes(StandardCharsets.UTF_8);
  var filter=new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws java.io.IOException,ServletException{
   String header=request.getHeader("Authorization");if(header!=null&&MessageDigest.isEqual(expected,header.getBytes(StandardCharsets.UTF_8)))SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("diagnostic-reader",null,List.of()));chain.doFilter(request,response);
  }};
  return http.sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/hello").permitAll().anyRequest().authenticated()).addFilterBefore(filter,AnonymousAuthenticationFilter.class).build();
 }
}
