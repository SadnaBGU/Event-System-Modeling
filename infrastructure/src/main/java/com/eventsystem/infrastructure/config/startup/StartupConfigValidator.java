package com.eventsystem.infrastructure.config.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupConfigValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    private final StartupProperties startupProperties;

    public StartupConfigValidator(StartupProperties startupProperties) {
        this.startupProperties = startupProperties;
    }

    @Override
    public void run(String... args) {
        try {
            startupProperties.validateOrThrow();
        } catch (StartupConfigException e) {
            log.error(e.getMessage());
            throw e;
        }

        log.info(
                "STARTUP_CONFIG_VALID: startup mode={}, init-state-file={}",
                startupProperties.getMode(),
                safeInitFileForLog(startupProperties.getInitStateFile())
        );
    }

    private String safeInitFileForLog(String path) {
        return path == null || path.isBlank() ? "<none>" : path;
    }
}