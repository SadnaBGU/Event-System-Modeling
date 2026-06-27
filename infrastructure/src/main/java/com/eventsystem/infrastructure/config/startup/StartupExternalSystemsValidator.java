package com.eventsystem.infrastructure.config.startup;

import com.eventsystem.application.system.IExternalSystemsAvailabilityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(-2)
public class StartupExternalSystemsValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupExternalSystemsValidator.class);

    private final IExternalSystemsAvailabilityPort externalSystemsAvailabilityPort;
    private final StartupProperties startupProperties;

    public StartupExternalSystemsValidator(
            IExternalSystemsAvailabilityPort externalSystemsAvailabilityPort,
            StartupProperties startupProperties) {
        this.externalSystemsAvailabilityPort = externalSystemsAvailabilityPort;
        this.startupProperties = startupProperties;
    }

    @Override
    public void run(String... args) {
        if (!startupProperties.isValidateExternalSystems()) {
            log.info("STARTUP_EXTERNAL_SYSTEMS_VALIDATION_SKIPPED");
            return;
        }

        try {
            if (!externalSystemsAvailabilityPort.areExternalSystemsAvailable()) {
                throw new StartupConfigException(
                        "STARTUP_EXTERNAL_SYSTEMS_ERROR: external payment/ticketing services are unavailable; startup halted."
                );
            }

            log.info("STARTUP_EXTERNAL_SYSTEMS_VALID: external payment/ticketing services are available.");
        } catch (StartupConfigException e) {
            log.error(e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            throw new StartupConfigException(
                    "STARTUP_EXTERNAL_SYSTEMS_ERROR: failed to validate external payment/ticketing services during startup.",
                    e
            );
        }
    }
}