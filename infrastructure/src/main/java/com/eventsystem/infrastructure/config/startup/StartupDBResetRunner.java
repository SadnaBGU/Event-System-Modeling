package com.eventsystem.infrastructure.config.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs after StartupConfigValidator and before AdminBootstrap.
 *
 * Ordering:
 * - StartupConfigValidator: Ordered.HIGHEST_PRECEDENCE
 * - this runner: -1
 * - AdminBootstrap: 0
 * - queue reset: 1
 * - InitFileRunner: 100
 */
@Component
@Order(-1)
public class StartupDBResetRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDBResetRunner.class);

    private final StartupProperties startupProperties;
    private final DBResetService databaseResetService;

    public StartupDBResetRunner(
            StartupProperties startupProperties,
            DBResetService dBResetService) {
        this.startupProperties = startupProperties;
        this.databaseResetService = dBResetService;
    }

    @Override
    public void run(String... args) {
        StartupMode mode = startupProperties.getMode();

        if (mode == StartupMode.EMPTY_DB || mode == StartupMode.INIT_FILE) {
            log.warn("STARTUP_DB_RESET_REQUESTED: startup mode={}", mode);
            databaseResetService.resetDatabase();
            return;
        }

        log.info("STARTUP_DB_RESET_SKIPPED: startup mode={}", mode);
    }
}