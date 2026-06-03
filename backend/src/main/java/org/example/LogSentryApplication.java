package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogSentryApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogSentryApplication.class, args);
    }
}
