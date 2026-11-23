package dev.evraktakip;

import dev.evraktakip.config.EvrakProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EvrakProperties.class)
public class EvrakTakipApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvrakTakipApplication.class, args);
    }
}
