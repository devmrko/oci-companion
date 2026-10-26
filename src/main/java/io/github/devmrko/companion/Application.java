package io.github.devmrko.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
public class Application {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(".data"));
        SpringApplication.run(Application.class, args);
    }
}
