package io.github.devmrko.companion.web;
import io.github.devmrko.companion.domain.Models.Connection;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean SecurityFilterChain security(HttpSecurity http,SecurityContextRepository repository, @Value("${companion.extra-hosts:}") String extraHosts) throws Exception {
        var allowedHosts=new HashSet<>(Set.of("localhost","127.0.0.1","[::1]","::1"));
        for(String host:extraHosts.split(","))if(!host.isBlank())allowedHosts.add(host.strip().toLowerCase(Locale.ROOT));
        http.authorizeHttpRequests(a -> a.requestMatchers("/login","/connect","/app.css","/error").permitAll().anyRequest().authenticated())
            .securityContext(c -> c.securityContextRepository(repository))
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> res.sendRedirect("/login")))
            .logout(l -> l.logoutUrl("/disconnect").logoutSuccessUrl("/login").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")))
            .addFilterBefore(new OncePerRequestFilter() {
                protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,HttpServletResponse response,jakarta.servlet.FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException {
                    if (!allowedHosts.contains(request.getServerName().toLowerCase(Locale.ROOT))) { response.sendError(403); return; }
                    response.setHeader("Cache-Control","no-store");
                    chain.doFilter(request,response);
                }
            },UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    public static Connection connection(org.springframework.security.core.Authentication auth) {
        if (auth==null || !(auth.getPrincipal() instanceof Connection c)) throw new org.springframework.security.access.AccessDeniedException("Connect first");
        return c;
    }
}
