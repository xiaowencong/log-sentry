package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.LogSourceService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements CommandLineRunner {

    private final LogSourceService logSourceService;

    @Override
    public void run(String... args) {
        log.info("Auto-starting enabled log sources...");
        logSourceService.startAllEnabled();
    }
}
